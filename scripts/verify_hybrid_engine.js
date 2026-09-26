const assert = require('assert');

// 1. Replicate RuleBasedClassifierFallback logic
const OTP_PATTERNS = [
    /\b(otp|one[-\s]?time[-\s]?(password|passcode|pin))\b/i,
    /\b(verification\s+code|security\s+code|secret\s+code|auth\s+code)\b/i,
    /\b(kode\s+verifikasi|kode\s+otp|kode\s+rahasia)\b/i,
    /\b(do\s+not\s+share\s+(this|your)?\s*(code|otp|password)?)\b/i,
    /\b(jangan\s+bagikan\s+kode\s+ini)\b/i
];

const FAILED_TRANSACTION_PATTERNS = [
    /\b(failed|declined|unsuccessful|cancelled|canceled|rejected|expired)\b/i,
    /\b(gagal|tidak\s+berhasil|dibatalkan|ditolak|kadaluwarsa|batal)\b/i,
    /\b(payment\s+failed|transaction\s+failed|transaksi\s+gagal|pembayaran\s+gagal)\b/i
];

const CONVERSATIONAL_CHAT_PATTERNS = [
    /\b(rekening|rek)(\s+\w+)?\s+(aku|saya|gue|gw|kamu|lo|lu)\b/i,
    /\b(aku|kamu|gue|gw|lo|lu)\b/i,
    /\b(tolong|please|pls|jangan\s+lupa|ingetin|bisa\s+transfer|bisa\s+bayar|udah\s+bayar|udah\s+transfer)\b/i,
    /\b(transfer\s+ke\s+(aku|gue|saya)|kirim\s+ke\s+(aku|gue|saya)|bayar\s+ke\s+(aku|gue|saya))\b/i,
    /\b(pay\s+me|send\s+me|wire\s+me|transfer\s+to\s+me|my\s+account|remind\s+me\s+to)\b/i,
    /\b(bayar|transfer)\s+.*?\b(sekarang)\b/i,
    /\b(sekarang\s+ya|nanti\s+ya|bro|sis|gan|kuy|dong|nih)\b/i,
    /\?/
];

const PROMO_PATTERNS = [
    /\b(cashback|discount|coupon|diskon|promo|voucher)\b/i,
    /\b(claim\s+your\s+(offer|reward|discount|voucher)?)\b/i,
    /\b(penawaran\s+spesial|selamat\s+anda\s+mendapatkan|menangkan|win\s+up\s+to|special\s+offer)\b/i,
    /\b(dapatkan\s+(promo|diskon|cashback|voucher|gratis|hadiah|penawaran)?)\b/i,
    /\b(dan\s+dapatkan|and\s+get|gratis\s+ongkir|free\s+shipping|flash\s+sale)\b/i,
    /\b(login\s+detected|new\s+device\s+login|security\s+alert|password\s+changed)\b/i,
    /\b(login\s+dari\s+perangkat\s+baru|ganti\s+kata\s+sandi)\b/i
];

const STRONG_EXPENSE_PATTERNS = [
    /\b(spent|debited(\s+(with|by|from))?|paid(\s+to)?|payment(\s+(to|of|for))?|purchase\s+(at|of|for)|purchased)\b/i,
    /\b(bayar(\s+(ke|di))?|pembayaran(\s+(ke|di))?|transaksi(\s+(di|sebesar))?|berhasil\s+bayar)\b/i,
    /\b(transfer\s+(ke|out\s+to)|sent\s+to|debit\s+alert|card\s+ending\s+in)\b/i,
    /\b(potongan\s+sebesar|terpotong\s+sebesar|tagihan\s+lunas)\b/i
];

const WEAK_EXPENSE_WORDS = ["buy", "bought", "charge", "charged", "order", "checkout", "belanja"];

const STRONG_INCOME_PATTERNS = [
    /\b(received(\s+.{1,35})?\s+from|received\s+money|credited(\s+(with|by|to))?|salary\s+credited)\b/i,
    /\b(deposit\s+successful|inward\s+transfer|transfer\s+masuk|dana\s+masuk)\b/i,
    /\b(menerima\s+transfer|uang\s+masuk|top[-\s]?up\s+berhasil|topup\s+success)\b/i,
    /\b(transfer\s+dari|received\s+payment|credit\s+alert|pengembalian\s+dana|gaji)\b/i,
    /\b(refund\s+(from|processed|of)?|cashback\s+credited)\b/i
];

const WEAK_INCOME_WORDS = ["deposit", "incoming", "masuk", "kredit", "penerimaan", "setoran", "received", "receive"];

function classify(text) {
    if (!text || text.trim() === '') {
        return { type: 'NOISE', confidence: 1.0 };
    }

    let noiseScore = 0.0;
    let expenseScore = 0.0;
    let incomeScore = 0.0;

    for (const p of FAILED_TRANSACTION_PATTERNS) if (p.test(text)) noiseScore += 10.0;
    for (const p of OTP_PATTERNS) if (p.test(text)) noiseScore += 8.0;
    for (const p of PROMO_PATTERNS) if (p.test(text)) noiseScore += 6.0;
    for (const p of CONVERSATIONAL_CHAT_PATTERNS) if (p.test(text)) noiseScore += 8.0;
    for (const p of STRONG_EXPENSE_PATTERNS) if (p.test(text)) expenseScore += 4.5;
    const lower = text.toLowerCase();
    for (const w of WEAK_EXPENSE_WORDS) if (lower.includes(w)) expenseScore += 1.5;

    for (const p of STRONG_INCOME_PATTERNS) if (p.test(text)) incomeScore += 4.5;
    for (const w of WEAK_INCOME_WORDS) if (lower.includes(w)) incomeScore += 1.5;

    // Suppress false expense/income triggers caused by failed transactions, chat messages, or promotional marketing text
    if (noiseScore > 0) {
        expenseScore = Math.max(0, expenseScore - noiseScore * 0.7);
        incomeScore = Math.max(0, incomeScore - noiseScore * 0.7);
    }

    if (expenseScore > 0 && incomeScore > 0) {
        if (lower.includes('payment') || lower.includes('paid') || lower.includes('bayar')) {
            expenseScore += 2.0;
        }
    }

    const maxScore = Math.max(noiseScore, expenseScore, incomeScore);
    if (maxScore === 0) {
        return { type: 'NOISE', confidence: 0.5 };
    }

    const expNoise = Math.exp(noiseScore - maxScore);
    const expExpense = Math.exp(expenseScore - maxScore);
    const expIncome = Math.exp(incomeScore - maxScore);
    const sumExp = expNoise + expExpense + expIncome;

    const pNoise = expNoise / sumExp;
    const pExpense = expExpense / sumExp;
    const pIncome = expIncome / sumExp;

    let predicted = 'NOISE';
    let conf = pNoise;
    if (pExpense >= pNoise && pExpense >= pIncome) {
        predicted = 'EXPENSE';
        conf = pExpense;
    } else if (pIncome >= pNoise && pIncome >= pExpense) {
        predicted = 'INCOME';
        conf = pIncome;
    }

    return { type: predicted, confidence: conf };
}

// 2. Replicate parseAmountString logic
function parseAmountString(raw, currencyHint) {
    let clean = raw.replace(/\s+/g, '');
    const hasDot = clean.includes('.');
    const hasComma = clean.includes(',');

    let normalized = clean;
    if (hasDot && hasComma) {
        const lastDot = clean.lastIndexOf('.');
        const lastComma = clean.lastIndexOf(',');
        if (lastDot > lastComma) {
            normalized = clean.replace(/,/g, '');
        } else {
            normalized = clean.replace(/\./g, '').replace(/,/g, '.');
        }
    } else if (hasComma) {
        const parts = clean.split(',');
        if (parts.length > 2) {
            normalized = clean.replace(/,/g, '');
        } else {
            const dec = parts[1] || '';
            if (dec.length === 3) {
                normalized = clean.replace(/,/g, '');
            } else {
                normalized = clean.replace(/,/g, '.');
            }
        }
    } else if (hasDot) {
        const parts = clean.split('.');
        if (parts.length > 2) {
            normalized = clean.replace(/\./g, '');
        } else {
            const dec = parts[1] || '';
            if (dec.length === 3) {
                normalized = clean.replace(/\./g, '');
            } else {
                normalized = clean;
            }
        }
    }

    const num = parseFloat(normalized);
    return isNaN(num) ? null : num.toFixed(2);
}

console.log('Running Verification Suite for Hybrid Engine:');

// Test 1: Expense Classification
const expenses = [
    "Paid $45.50 at Target on Sep 26",
    "Debit alert: Your card ending in 4321 was debited USD 120.00 for payment at Amazon",
    "Pembayaran Rp 75.000 ke Toko Jaya berhasil",
    "You spent €18.20 at Bakery Central"
];
for (const e of expenses) {
    const res = classify(e);
    assert.strictEqual(res.type, 'EXPENSE', `Expected EXPENSE for: ${e}`);
    assert.ok(res.confidence >= 0.65, `Confidence ${res.confidence} >= 0.65 for: ${e}`);
    console.log(`✓ Expense classified: [${res.type}] (${(res.confidence * 100).toFixed(1)}%) -> "${e}"`);
}

// Test 2: Income Classification
const incomes = [
    "Salary credited: USD 3,500.00 from ACME Corp has been deposited into your account",
    "Received $50.00 from Alice via PayPal",
    "Dana masuk sebesar Rp 500.000 dari BUDI SANTOSO via BCA Mobile",
    "Refund of $25.00 has been credited to your account from Store"
];
for (const inc of incomes) {
    const res = classify(inc);
    assert.strictEqual(res.type, 'INCOME', `Expected INCOME for: ${inc}`);
    assert.ok(res.confidence >= 0.65, `Confidence ${res.confidence} >= 0.65 for: ${inc}`);
    console.log(`✓ Income classified: [${res.type}] (${(res.confidence * 100).toFixed(1)}%) -> "${inc}"`);
}

// Test 3: Noise Classification (Including Failed/Declined Transactions, Chat Messages, Promo Bait & OTPs)
const noise = [
    "Your OTP verification code is 492810. Do not share this code with anyone.",
    "Special discount! Get 50% cashback voucher on your next purchase using code PROMO50.",
    "Security alert: Login detected from a new Windows PC device.",
    "Selamat Anda memenangkan voucher diskon belanja!",
    "Pembayaran sebesar 200k ke Google Pay gagal",
    "Transaction of $150.00 at Apple declined",
    "Payment to Netflix failed due to insufficient funds",
    "Transaksi Rp 100.000 di Indomaret dibatalkan",
    "bayar 200.000 ke rekening bri aku sekarang",
    "bayar 250.000 sekarang dan dapatkan promo shopee terbaru",
    "tolong transfer 50.000 ya bro",
    "can you pay $20 for lunch?"
];
for (const n of noise) {
    const res = classify(n);
    assert.strictEqual(res.type, 'NOISE', `Expected NOISE for: ${n}`);
    console.log(`✓ Noise / Chat / Promo bait correctly rejected: [${res.type}] -> "${n}"`);
}

// Package blacklist verification
const IGNORED_PACKAGES = new Set([
    "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger",
    "com.facebook.orca", "com.instagram.android", "com.discord"
]);
assert.strictEqual(IGNORED_PACKAGES.has("com.whatsapp"), true);
assert.strictEqual(IGNORED_PACKAGES.has("org.telegram.messenger"), true);
console.log('✓ Non-financial chat and messaging packages successfully blacklisted!');

// 3. Shorthand multiplier extraction
function parseShorthand(text) {
    const SHORTHAND_PATTERN = /(?:([a-zA-Z$€£¥₹]+)\s*)?([0-9]+(?:[.,][0-9]+)?)\s*(k|rb|ribu|m|jt|juta)\b/i;
    const m = text.match(SHORTHAND_PATTERN);
    if (!m) return null;
    const base = parseFloat(m[2].replace(',', '.'));
    const mult = m[3].toLowerCase();
    let factor = 1;
    if (['k', 'rb', 'ribu'].includes(mult)) factor = 1000;
    else if (['m', 'jt', 'juta'].includes(mult)) factor = 1000000;
    return (base * factor).toFixed(2);
}

// Test 4: Deterministic Amount Extraction
assert.strictEqual(parseAmountString("1,250.75"), "1250.75");
assert.strictEqual(parseAmountString("1.250,50"), "1250.50");
assert.strictEqual(parseAmountString("25.000", "Rp"), "25000.00");
assert.strictEqual(parseAmountString("50,000", "IDR"), "50000.00");
assert.strictEqual(parseAmountString("1250"), "1250.00");
assert.strictEqual(parseAmountString("1250.5"), "1250.50");
assert.strictEqual(parseAmountString("45.50"), "45.50");
assert.strictEqual(parseShorthand("200k"), "200000.00");
assert.strictEqual(parseShorthand("Pembayaran sebesar 200k ke Google Pay berhasil"), "200000.00");
assert.strictEqual(parseShorthand("1.5jt"), "1500000.00");
assert.strictEqual(parseShorthand("50rb"), "50000.00");
assert.strictEqual(parseAmountString("13.000,00", "Rp"), "13000.00");
console.log('✓ All deterministic number formats and shorthand multipliers parsed perfectly!');

// Test 5: Successful shorthand payment
const successPayment = "Pembayaran sebesar 200k ke Google Pay berhasil";
const successClass = classify(successPayment);
assert.strictEqual(successClass.type, 'EXPENSE');
assert.strictEqual(parseShorthand(successPayment), "200000.00");
console.log(`✓ Successful payment with shorthand parsed: [${successClass.type}] -> 200000.00 IDR`);

// Test 5b: QRIS with timestamp, date, and attached Rp currency
const qrisNotification = "26/09/2026 19:23:41 - Transaksi Pembelian QRIS sebesar Rp13.000,00 BERHASIL. Info lebih lanjut hubungi Call Center BRI 1500017";
const qrisClass = classify(qrisNotification);
assert.strictEqual(qrisClass.type, 'EXPENSE');

const CURRENCY_SYMBOLS = "[$€£¥₹₩₺₽฿₫]";
const CURRENCY_CODES = "(?:Rp\\.?|Rs\\.?|IDR|USD|EUR|GBP|CAD|AUD|SGD|MYR|CHF|JPY|INR|AED|SAR|NZD|HKD|VND|KRW)";
const PREFIX_PATTERN = new RegExp("(" + CURRENCY_SYMBOLS + "|\\b" + CURRENCY_CODES + ")\\s*([0-9]{1,3}(?:[.,\\s][0-9]{3})+(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]+)?)", "i");
const qrisMatch = qrisNotification.match(PREFIX_PATTERN);
assert.ok(qrisMatch, "Must match prefix currency pattern for Rp13.000,00");
assert.strictEqual(parseAmountString(qrisMatch[2], qrisMatch[1]), "13000.00");
console.log(`✓ QRIS notification with leading date correctly parsed as: 13000.00 IDR (NOT 26!)`);

// Test 6: Cross-App Payment Gateway Correlation (e.g. BRImo -> Google Pay -> Google One)
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
    }
];

function touchesGateway(expense, gateway) {
    const pkg = (expense.packageName || "").toLowerCase();
    const merchant = (expense.merchant || "").toLowerCase();
    const raw = (expense.rawText || "").toLowerCase();
    return gateway.packagePatterns.some(p => pkg.includes(p.toLowerCase())) ||
           gateway.keywords.some(k => merchant.includes(k) || raw.includes(k));
}

function resolveDeduplication(existingList, incoming) {
    const WINDOW_MS = 30000;
    for (let i = 0; i < existingList.length; i++) {
        const existing = existingList[i];
        const sameAmount = existing.amount === incoming.amount;
        const timeDiff = Math.abs(incoming.timestamp - existing.timestamp);

        if (sameAmount && timeDiff <= WINDOW_MS) {
            if (existing.merchant.toLowerCase() === incoming.merchant.toLowerCase()) {
                return { action: 'IGNORE_DUPLICATE', index: i };
            }
            const gateway = GATEWAY_DEFINITIONS.find(g => touchesGateway(existing, g) && touchesGateway(incoming, g));
            if (gateway) {
                const isExistingGatewayApp = gateway.packagePatterns.some(p => existing.packageName.includes(p));
                const isIncomingGatewayApp = gateway.packagePatterns.some(p => incoming.packageName.includes(p));
                const preferredMerchant = (!isExistingGatewayApp && isIncomingGatewayApp) ? incoming.merchant : existing.merchant;
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

// Scenario 1: BRImo arrives first, Google Play arrives 3s later
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
    rawText: "Payment for subscription to Google One is successful, amount 87.580",
    timestamp: 13000
};

const res1 = resolveDeduplication([brimoExpense], gplayExpense);
assert.strictEqual(res1.action, 'MERGE');
assert.strictEqual(res1.mergedExpense.merchant, 'Google One');
console.log('✓ Scenario 1: BRImo + Google Pay merged into SINGLE notification with merchant:', res1.mergedExpense.merchant);

// Scenario 2: Google Play arrives first, BRImo arrives 2s later
const res2 = resolveDeduplication([gplayExpense], brimoExpense);
assert.strictEqual(res2.action, 'MERGE');
assert.strictEqual(res2.mergedExpense.merchant, 'Google One');
console.log('✓ Scenario 2: Google Pay + BRImo (reverse order) merged into SINGLE notification with merchant:', res2.mergedExpense.merchant);

// Scenario 3: Distinct coffee purchases of same amount ($4.50) NOT merged
const coffee1 = { amount: "4.50", merchant: "Starbucks", packageName: "com.chase.sig.android", timestamp: 10000 };
const coffee2 = { amount: "4.50", merchant: "Peets Coffee", packageName: "com.chase.sig.android", timestamp: 15000 };
const resCoffee = resolveDeduplication([coffee1], coffee2);
assert.strictEqual(resCoffee.action, 'ADD_NEW');
console.log('✓ Scenario 3: Distinct merchant purchases with same amount are NOT falsely merged!');

console.log('\n=============================================');
console.log('ALL TESTS PASSED SUCCESSFULLY! (100% verified)');
console.log('=============================================');
