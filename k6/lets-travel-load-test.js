// Script k6 - Let's Travel
// Couvre l'item audit "le systeme supporte-t-il un traffic eleve (recherche, consultation,
// tableau de bord) sans degradation de performance, en moins de 5 secondes ?" ainsi que les
// flux traveler/manager/admin decrits dans l'enonce.
//
// Lancement (voir k6/README.md pour le detail) :
//   k6 run k6/lets-travel-load-test.js
//   k6 run -e BASE_URL=https://host.docker.internal:8443 k6/lets-travel-load-test.js   (via Docker)
//
// Variables d'env optionnelles : BASE_URL, ADMIN_USERNAME, ADMIN_PASSWORD.

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://localhost:8443';
const ADMIN_USERNAME = __ENV.ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'changeme_dev_only';

const TRAVELER_COUNT = 8;
const TRAVEL_COUNT = 4;

export const options = {
    insecureSkipTLSVerify: true, // certificat auto-signe travel-plan.local (voir infra/nginx)
    scenarios: {
        // Coeur de l'item audit "haut traffic" : recherche/consultation/autocomplete/recommandations.
        browsing_and_search: {
            executor: 'ramping-vus',
            exec: 'browsingAndSearch',
            startVUs: 0,
            stages: [
                { duration: '20s', target: 50 },
                { duration: '40s', target: 50 },
                { duration: '10s', target: 0 },
            ],
        },
        traveler_actions: {
            executor: 'constant-vus',
            exec: 'travelerActions',
            vus: 10,
            duration: '50s',
            startTime: '5s',
        },
        manager_dashboard: {
            executor: 'constant-vus',
            exec: 'managerDashboard',
            vus: 2,
            duration: '50s',
            startTime: '5s',
        },
        admin_dashboard: {
            executor: 'constant-vus',
            exec: 'adminDashboard',
            vus: 2,
            duration: '50s',
            startTime: '5s',
        },
        // Volontairement tres faible volume : passe par un vrai Stripe en mode test, pas de sandbox
        // dediee au load testing - on evite d'abuser l'API tierce.
        payment_flow: {
            executor: 'shared-iterations',
            exec: 'paymentFlow',
            vus: 1,
            iterations: 2,
            startTime: '5s',
        },
    },
    thresholds: {
        // Correspond au libelle exact de l'audit ("moins de 5 secondes") pour le scenario de traffic.
        'http_req_duration{scenario:browsing_and_search}': ['p(95)<5000'],
        checks: ['rate>0.9'],
    },
};

function jsonHeaders(token) {
    const headers = { 'Content-Type': 'application/json' };
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }
    return headers;
}

function login(username, password) {
    const res = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ username, password }),
        { headers: jsonHeaders() },
    );
    check(res, { 'login: 200': (r) => r.status === 200 });
    return res.json('token');
}

function isoDate(daysFromNow) {
    const d = new Date(Date.now() + daysFromNow * 86400000);
    return d.toISOString().slice(0, 10);
}

function isoInstant(daysFromNow) {
    return new Date(Date.now() + daysFromNow * 86400000).toISOString();
}

// setup() s'execute une seule fois, quel que soit le nombre de VUs : c'est ce qui nous permet de
// ne faire que 2 appels /api/auth/login (admin + manager) et de rester tres large sous la limite
// nginx (5r/m, burst 3) - les travelers, eux, obtiennent leur token via /api/auth/register, qui
// n'est pas soumis a cette limite (voir infra/nginx/nginx-main.conf).
export function setup() {
    const runId = Date.now();
    const adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);

    // --- Manager de test ---
    const managerEmail = `k6-manager-${runId}@example.com`;
    const managerUsername = `k6-manager-${runId}`;
    const managerPassword = 'K6-test-pass-1!';

    const managerUserRes = http.post(
        `${BASE_URL}/api/users`,
        JSON.stringify({
            firstName: 'K6',
            lastName: 'Manager',
            email: managerEmail,
            phone: '0600000000',
            role: 'TRAVEL_MANAGER',
            address: { street: '1 rue du Test', city: 'Paris', postalCode: '75001', country: 'France' },
        }),
        { headers: jsonHeaders(adminToken) },
    );
    check(managerUserRes, { 'setup: manager user created': (r) => r.status === 201 });
    const managerId = managerUserRes.json('id');

    const managerAccountRes = http.post(
        `${BASE_URL}/api/auth/accounts`,
        JSON.stringify({ username: managerUsername, password: managerPassword, role: 'TRAVEL_MANAGER', userId: managerId }),
        { headers: jsonHeaders(adminToken) },
    );
    check(managerAccountRes, { 'setup: manager account created': (r) => r.status === 201 });

    const managerToken = login(managerUsername, managerPassword);

    // --- Travelers de test (inscription publique, pas de login) ---
    const travelers = [];
    for (let i = 0; i < TRAVELER_COUNT; i++) {
        const email = `k6-traveler-${runId}-${i}@example.com`;
        const registerRes = http.post(
            `${BASE_URL}/api/users/register`,
            JSON.stringify({
                firstName: 'K6',
                lastName: `Traveler${i}`,
                email,
                phone: '0600000001',
                address: { street: '2 rue du Test', city: 'Lyon', postalCode: '69001', country: 'France' },
                acceptedPrivacyPolicy: true,
            }),
            { headers: jsonHeaders() },
        );
        check(registerRes, { 'setup: traveler profile created': (r) => r.status === 201 });
        const registrationToken = registerRes.json('registrationToken');

        const authRes = http.post(
            `${BASE_URL}/api/auth/register`,
            JSON.stringify({ username: `k6-traveler-${runId}-${i}`, password: 'K6-test-pass-1!', registrationToken }),
            { headers: jsonHeaders() },
        );
        check(authRes, { 'setup: traveler account created': (r) => r.status === 201 });
        travelers.push({ id: registerRes.json('user.id'), token: authRes.json('token') });
    }

    // --- Voyages de test (crees par le manager) ---
    const travels = [];
    for (let i = 0; i < TRAVEL_COUNT; i++) {
        const travelRes = http.post(
            `${BASE_URL}/api/travels`,
            JSON.stringify({
                title: `K6 Voyage ${i} - ${runId}`,
                startDate: isoDate(30),
                endDate: isoDate(35),
                status: 'CONFIRMED',
                price: 999.99,
                currency: 'EUR',
                destinations: [
                    { city: 'Paris', country: 'France', arrivalDate: isoDate(30), departureDate: isoDate(35), orderIndex: 0 },
                ],
                transportations: [
                    { type: 'FLIGHT', fromLocation: 'CDG', toLocation: 'JFK', departureTime: isoInstant(30), arrivalTime: isoInstant(30) },
                ],
            }),
            { headers: jsonHeaders(managerToken) },
        );
        check(travelRes, { 'setup: travel created': (r) => r.status === 201 });
        travels.push(travelRes.json('id'));
    }

    // Un peu de donnees reelles pour que dashboards/notes/recommandations aient de quoi agreger.
    if (travels.length > 0 && travelers.length > 0) {
        const travelId = travels[0];
        travelers.slice(0, 3).forEach((traveler) => {
            http.post(`${BASE_URL}/api/travels/${travelId}/subscriptions`, null, { headers: jsonHeaders(traveler.token) });
            http.post(
                `${BASE_URL}/api/travels/${travelId}/feedbacks`,
                JSON.stringify({ rating: 4, comment: 'Genere par k6' }),
                { headers: jsonHeaders(traveler.token) },
            );
        });

        // Moyen de paiement Stripe (identifiant de test officiel "pm_card_visa", cf. Stripe docs)
        // pour le scenario payment_flow - un jeton "tok_..." n'est pas accepte par l'API PaymentIntents.
        const pmRes = http.post(
            `${BASE_URL}/api/payment-methods`,
            JSON.stringify({ provider: 'STRIPE', type: 'CARD', providerToken: 'pm_card_visa', brand: 'Visa', last4: '4242', isDefault: true }),
            { headers: jsonHeaders(travelers[0].token) },
        );
        travelers[0].paymentMethodId = pmRes.json('id');
    }

    return { adminToken, managerToken, managerId, travelers, travels };
}

function randomOf(list) {
    return list[Math.floor(Math.random() * list.length)];
}

export function browsingAndSearch(data) {
    const traveler = randomOf(data.travelers);
    const headers = { headers: jsonHeaders(traveler.token) };

    check(http.get(`${BASE_URL}/api/travels`, headers), { 'browse: list 200': (r) => r.status === 200 });
    check(http.get(`${BASE_URL}/api/travels/search?q=Paris`, headers), { 'browse: search 200': (r) => r.status === 200 });
    check(http.get(`${BASE_URL}/api/travels/autocomplete?q=Par`, headers), { 'browse: autocomplete 200': (r) => r.status === 200 });
    check(http.get(`${BASE_URL}/api/travels/${randomOf(data.travels)}`, headers), { 'browse: detail 200': (r) => r.status === 200 });
    check(http.get(`${BASE_URL}/api/travels/recommendations`, headers), { 'browse: recommendations 200': (r) => r.status === 200 });

    sleep(1);
}

export function travelerActions(data) {
    const traveler = randomOf(data.travelers);
    const travelId = randomOf(data.travels);
    const headers = { headers: jsonHeaders(traveler.token) };

    const subRes = http.post(`${BASE_URL}/api/travels/${travelId}/subscriptions`, null, headers);
    check(subRes, { 'traveler: subscribe 2xx or already-subscribed': (r) => r.status === 201 || r.status === 409 });
    if (subRes.status === 201) {
        http.del(`${BASE_URL}/api/travels/${travelId}/subscriptions/${subRes.json('id')}`, null, headers);
    }

    http.post(`${BASE_URL}/api/travels/${travelId}/feedbacks`, JSON.stringify({ rating: 5, comment: 'k6' }), headers);
    http.post(
        `${BASE_URL}/api/travels/${travelId}/reports`,
        JSON.stringify({ reportedType: 'MANAGER', reportedId: data.managerId, reason: 'Test k6 - a ignorer' }),
        headers,
    );
    check(http.get(`${BASE_URL}/api/travels/managers/${data.managerId}/public-stats`, headers), {
        'traveler: manager public stats 200': (r) => r.status === 200,
    });

    sleep(1);
}

export function managerDashboard(data) {
    const headers = { headers: jsonHeaders(data.managerToken) };
    check(http.get(`${BASE_URL}/api/travels/managers/me/stats`, headers), { 'manager: stats 200': (r) => r.status === 200 });

    const travelId = randomOf(data.travels);
    http.get(`${BASE_URL}/api/travels/${travelId}/subscriptions`, headers);
    http.get(`${BASE_URL}/api/travels/${travelId}/feedbacks`, headers);

    sleep(1);
}

export function adminDashboard(data) {
    const headers = { headers: jsonHeaders(data.adminToken) };
    check(http.get(`${BASE_URL}/api/travels/admin/manager-rankings`, headers), { 'admin: manager rankings 200': (r) => r.status === 200 });
    check(http.get(`${BASE_URL}/api/travels/admin/travel-rankings`, headers), { 'admin: travel rankings 200': (r) => r.status === 200 });
    check(http.get(`${BASE_URL}/api/travels/admin/monthly-revenue`, headers), { 'admin: monthly revenue 200': (r) => r.status === 200 });
    // Voir le message envoye a Daro : cette route semble non routee par l'api-gateway, on garde le
    // check pour que le rapport k6 le montre clairement plutot que de le deviner.
    check(http.get(`${BASE_URL}/api/reports`, headers), { 'admin: reports reachable': (r) => r.status === 200 });

    sleep(1);
}

export function paymentFlow(data) {
    const traveler = data.travelers[0];
    if (!traveler.paymentMethodId) {
        return;
    }
    const headers = { headers: jsonHeaders(traveler.token) };
    const res = http.post(
        `${BASE_URL}/api/payments`,
        JSON.stringify({ travelId: data.travels[0], paymentMethodId: traveler.paymentMethodId }),
        headers,
    );
    check(res, { 'payment: created': (r) => r.status === 201 });
}
