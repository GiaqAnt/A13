import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { parseJwt } from "./util.js";
import { scenario } from 'k6/execution';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

// Endpoint ngrok
const ENDPOINT = "https://c0c7-143-225-28-157.ngrok-free.app";

// Codici utente da testare per StringParser
const users = JSON.parse(open('./users.json'));
const code = [
    open('./TestCode/TestStringParser_empty.java'),
    open('./TestCode/TestStringParser_notCompile.java'),
    open('./TestCode/TestStringParser_win.java')
];

// Metriche personalizzate
export let runDuration          = new Trend('run_duration');            // Durata (ms) di ogni chiamata all'endpoint /run
export let runRetries           = new Counter('run_retries');           // Numero totale di retry su /run a causa di timeout
export let runRetryDelay        = new Trend('run_retry_delay');         // Tempo (s) di attesa per ogni singolo retry
export let runFailures          = new Rate('run_failure');              // Tasso di fallimento delle chiamate a /run
export let runGatewayTimeout    = new Counter('run_gateway_timeout');   // Numero di errori 504 GATEWAY_TIMEOUT ricevuti da /run
export let successfulRuns       = new Counter('run_successful');        // Numero di chiamate /run completate con successo
export let failedRuns           = new Counter('run_failed');            // Numero totale di chiamate /run fallite (qualsiasi causa)
export let timeoutFailureCount  = new Counter('timeout_failures');      // Chiamate fallite definitivamente per superamento retry su timeout

export let retryAttemptsStats   = new Trend('run_retry_count');         // Numero di retry per ogni chiamata a /run andata in timeout
export let totalRetryDelayStats = new Trend('run_total_retry_delay');   // Tempo totale (s) di attesa cumulata nei retry per singola /run

export let checkEmptyCode       = new Counter('check_empty_code');      // Codice vuoto compilato correttamente (caso test 0)
export let checkNotCompilable   = new Counter('check_not_compilable');  // Codice non compilabile (caso test 1)
export let checkHighCoverage    = new Counter('check_high_coverage');   // Codice con copertura >= robot (caso test 2)

// Configurazione
export const options = {
    ext: {
        influxdb: {
            url: 'http://localhost:9090',
            database: 'k6db',
            precision: 's',
        },
    },
    scenarios: {
        gradual_test: {
            executor: 'per-vu-iterations',
            vus: 50,
            iterations: 5,
            maxDuration: '120m',
        },
    },
};

export default function () {
    startGame();
}

// ==============================
// Funzioni principali
// ==============================
function weightedRandom() {
    const weights = [0.2, 0.1, 0.7]; // 0, 1, 2
    const r = Math.random();
    let acc = 0;
    for (let i = 0; i < weights.length; i++) {
        acc += weights[i];
        if (r < acc) return i;
    }
    return weights.length - 1;
}

function startGame() {
    sleep(Math.random() * 9 + 1);
    const vuIndex = __VU - 1;
    const user = users[vuIndex % users.length];
    const email = user.email;
    const password = user.password;

    const loginPayload = { email, password };
    const loginHeader = { headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, redirects: 0 };
    let resLogin = http.post(`${ENDPOINT}/login`, loginPayload, loginHeader);

    if (!(check(resLogin, { 'Login success': (r) => r.status === 200 || r.status === 302 }))) return;

    let jwt = resLogin.cookies?.jwt?.[0]?.value;
    let playerId = parseJwt(jwt).userId;
    sleep(Math.random() * 9 + 1);

    const mainHeader = { cookies: { lang: 'en', jwt } };
    http.get(`${ENDPOINT}/main`, null, mainHeader);

    const gameModeHeader = { "Content-Type": "application/json" };
    http.get(`${ENDPOINT}/session/gamemode/${playerId}?mode=PartitaSingola`, null, gameModeHeader);

    const startGamePayload = JSON.stringify({
        playerId, typeRobot: "Randoop", difficulty: "1", mode: "PartitaSingola",
        underTestClassName: "StringParser", remainingTime: 7200
    });

    const startGameHeader = {
        headers: { 'Content-Type': 'application/json' },
        cookies: { lang: 'en', jwt }
    };

    let startGameRes = http.post(`${ENDPOINT}/StartGame`, startGamePayload, startGameHeader);
    check(startGameRes, { 'StartGame success': (r) => r.status === 200 });
    sleep(Math.random() * 9 + 1);

    const timeToExit = scenario.time_remaining - (Math.random() * (5 - 2) * 60 * 1000 + 2 * 60 * 1000);
    let canWin = false;

    group('T5 /run', () => {
        while (true) {
            canWin = runGame(playerId, jwt, email);
            if ((canWin && Math.random() < 0.25) || (!canWin && Math.random() < 0.05)) break;
            if (scenario.time_remaining <= timeToExit) break;
        }
    });

    endGame(playerId, jwt, email, canWin);
}

function runGame(playerId, jwt, email) {
    const selectedCode = weightedRandom();
    const testCode = code[selectedCode];

    let retryCount = 0;
    let baseDelay = 10;
    let totalDelay = 0;
    const maxRetries = 10;

    const runPayload = JSON.stringify({
        playerId,
        mode: "PartitaSingola",
        testingClassCode: testCode,
        remainingTime: 7000,
    });

    const runHeader = {
        headers: { 'Content-Type': 'application/json' },
        cookies: { lang: 'en', jwt },
        timeout: '600s'
    };

    while (retryCount < maxRetries) {
        const startTime = Date.now();
        let res = http.post(`${ENDPOINT}/run`, runPayload, runHeader);
        const duration = Date.now() - startTime;

        runDuration.add(duration); // Tempo di risposta

        const isTimeout = res.status === 504 || (res.body && res.body.includes('504 GATEWAY_TIMEOUT'));
        if (isTimeout) {
            retryCount++;
            runGatewayTimeout.add(1);
            runRetries.add(retryCount);

            const delay = Math.random() * 10 + baseDelay * retryCount;
            totalDelay += delay;
            runRetryDelay.add(delay);

            if (retryCount === maxRetries) {
                // Timeout definitivo
                timeoutFailureCount.add(1);
                runFailures.add(1);
                failedRuns.add(1);
                retryAttemptsStats.add(retryCount);
                totalRetryDelayStats.add(totalDelay);
                console.error(`Timeout definitivo per ${email} dopo ${retryCount} tentativi`);
                return false;
            }

            console.warn(`Tentativo ${retryCount} fallito per timeout. Ritento tra ${delay.toFixed(2)}s...`);
            sleep(delay);
            continue;
        }

        try {
            const body = JSON.parse(res.body);
            const compilationResult = body.userCoverageDetails?.xml_coverage;
            const userScore = body.userScore;
            const robotScore = body.robotScore;
            const canWin = body.canWin;

            if (res.status > 299) {
                runFailures.add(1);
                failedRuns.add(1);
            } else {
                successfulRuns.add(1);
            }

            const checks = {
                0: { name: '/run codice vuoto', fn: () => compilationResult !== null && userScore === 0, metric: checkEmptyCode },
                1: { name: '/run codice non compilabile', fn: () => compilationResult === null, metric: checkNotCompilable },
                2: { name: '/run codice ad alta copertura', fn: () => compilationResult !== null && userScore >= robotScore, metric: checkHighCoverage },
            };

            const checkResult = check(res, {
                [checks[selectedCode].name]: checks[selectedCode].fn
            });
            if (checkResult) checks[selectedCode].metric.add(1);

            retryAttemptsStats.add(retryCount); // anche se è 0
            totalRetryDelayStats.add(totalDelay);

            sleep(Math.random() * 9 + 1);
            return canWin;

        } catch (e) {
            runFailures.add(1);
            failedRuns.add(1);
            console.error(`Errore nel parsing JSON o logica di risposta per ${email}`, e);
            return false;
        }
    }

    // Fallback finale se esce dal ciclo
    runFailures.add(1);
    failedRuns.add(1);
    timeoutFailureCount.add(1);
    retryAttemptsStats.add(retryCount);
    totalRetryDelayStats.add(totalDelay);
    return false;
}

function endGame(playerId, jwt, email, canWin) {
    const payload = JSON.stringify({
        playerId,
        mode: "PartitaSingola",
        testingClassCode: "",
        remainingTime: 1
    });

    const headers = {
        headers: { 'Content-Type': 'application/json' },
        cookies: { lang: 'en', jwt }
    };

    let res = http.post(`${ENDPOINT}/EndGame`, payload, headers);
    check(res, { 'EndGame success': (r) => r.status === 200 });
    sleep(Math.random() * 9 + 1);
}

export function handleSummary(data) {
    return {
        'summary.json': JSON.stringify(data, null, 2),
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
    };
}