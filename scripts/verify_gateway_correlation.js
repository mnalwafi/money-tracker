const assert = require('assert');

const GATEWAY_DEFINITIONS = [
    {
        id: "google",
        packagePatterns: ["com.google.android.apps.walletnfcrel", "com.android.vending", "com.google.android.apps.nbu.paisa"],
        keywords: ["google", "google pay", "google play", "gpay", "google one"]
    },
    {
        id: "paypal",
        packagePatterns: ["com.paypal.android.p2pmobile"],
        keywords: ["paypal"]
    },
    {
        id: "gopay",
        packagePatterns: ["com.gojek.app"],
        keywords: ["gopay", "gojek"]
    },
    {
        id: "ovo",
        packagePatterns: ["ovo.id"],
        keywords: ["ovo"]
    },
    {
        id: "dana",
        packagePatterns: ["id.dana"],
        keywords: ["dana"]
    },
    {
        id: "shopeepay",
        packagePatterns: ["com.shopee.id", "com.shopee.app"],
        keywords: ["shopeepay", "shopee pay", "spay"]
    }
];

function touchesGateway(expense, gateway) {
    const pkg = (expense.packageName || "").toLowerCase();
    const merchant = (expense.merchant || "").toLowerCase();
    const raw = (expense.rawText || "").toLowerCase();

    const matchesPackage = gateway.packagePatterns.some(p => pkg.includes(p.toLowerCase()));
    const matchesKeyword = gateway.keywords.some(k => merchant.includes(k) || raw.includes(k));

    return matchesPackage || matchesKeyword;
}

function findGatewayCorrelation(existing, incoming) {
    for (const g of GATEWAY_DEFINITIONS) {
        if (touchesGateway(existing, g) && touchesGateway(incoming, g)) {
            return g;
        }
    }
    return null;
}

function resolveDeduplication(existingList, incoming) {
    const WINDOW_MS = 30000;

    for (let i = 0; i < existingList.length; i++) {
        const existing = existingList[i];
        const sameAmount = existing.amount === incoming.amount;
        const timeDiff = Math.abs(incoming.timestamp - existing.timestamp);

        if (sameAmount && timeDiff <= WINDOW_MS) {
            // Case 1: Exact duplicate
            if (existing.merchant.toLowerCase() === incoming.merchant.toLowerCase()) {
                return { action: 'IGNORE_DUPLICATE', index: i };
            }

            // Case 2: Cross-app gateway correlation
            const gateway = findGatewayCorrelation(existing, incoming);
            if (gateway) {
                // Determine which merchant is more descriptive
                // The gateway app (e.g. Google Play) usually has the specific subscription/item (Google One)
                // whereas the bank only sees the gateway name (Google Pay)
                const isExistingGatewayApp = gateway.packagePatterns.some(p => existing.packageName.includes(p));
                const isIncomingGatewayApp = gateway.packagePatterns.some(p => incoming.packageName.includes(p));

                let preferredMerchant = existing.merchant;
                if (!isExistingGatewayApp && isIncomingGatewayApp) {
                    // Upgrade generic bank merchant ("Google Pay") to specific end merchant ("Google One")
                    preferredMerchant = incoming.merchant;
                }

                return {
                    action: 'MERGE',
                    index: i,
                    mergedExpense: {
                        ...existing,
                        merchant: preferredMerchant,
                        rawText: `${existing.rawText} | ${incoming.rawText}`
                    }
                };
            }
        }
    }

    return { action: 'ADD_NEW' };
}

console.log('Testing Cross-App Gateway Deduplication:');

// Test Case 1: BRImo arrives first, Google Play arrives 3 seconds later
const brimoExpense = {
    amount: "87580.00",
    merchant: "Google Pay",
    packageName: "id.co.bri.brimo",
    rawText: "Transfer ke GOOGLE PAY sebesar Rp 87.580 berhasil",
    timestamp: 10000
};

const gplayExpense = {
    amount: "87580.00",
    merchant: "Google One",
    packageName: "com.android.vending",
    rawText: "Payment for subscription to Google One is successful, amount Rp 87.580",
    timestamp: 13000
};

const res1 = resolveDeduplication([brimoExpense], gplayExpense);
assert.strictEqual(res1.action, 'MERGE');
assert.strictEqual(res1.mergedExpense.merchant, 'Google One');
console.log('✓ Scenario 1: BRImo + Google Pay merged into SINGLE notification with merchant:', res1.mergedExpense.merchant);

// Test Case 2: Google Play arrives first, BRImo arrives 2 seconds later
const res2 = resolveDeduplication([gplayExpense], brimoExpense);
assert.strictEqual(res2.action, 'MERGE');
assert.strictEqual(res2.mergedExpense.merchant, 'Google One');
console.log('✓ Scenario 2: Google Pay + BRImo (reverse order) merged into SINGLE notification with merchant:', res2.mergedExpense.merchant);

// Test Case 3: Two separate coffees at Starbucks ($4.50 each)
const coffee1 = {
    amount: "4.50",
    merchant: "Starbucks",
    packageName: "com.chase.sig.android",
    rawText: "Paid $4.50 at Starbucks",
    timestamp: 10000
};
const coffee2 = {
    amount: "4.50",
    merchant: "Starbucks Drive-Thru",
    packageName: "com.chase.sig.android",
    rawText: "Paid $4.50 at Starbucks Drive-Thru",
    timestamp: 15000
};
const resCoffee = resolveDeduplication([coffee1], coffee2);
assert.strictEqual(resCoffee.action, 'ADD_NEW');
console.log('✓ Scenario 3: Distinct merchant purchases with same amount are NOT falsely merged! Action:', resCoffee.action);

console.log('\nALL GATEWAY TESTS PASSED!');
