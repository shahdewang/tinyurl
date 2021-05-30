import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from "k6/data";

// not using SharedArray here will mean that the code in the function call (that is what loads and
// parses the json) will be executed per each VU which also means that there will be a complete copy
// per each VU
const data = new SharedArray("URL's", function() { return JSON.parse(open('./urls.json')).url; });

export let options = {
    stages: [
        { duration: '1m', target: 200 }, // simulate ramp-up of traffic from 1 to 200 users over 1 minute.
        { duration: '2m', target: 200 }, // stay at 200 users for 2 minutes
        { duration: '1m', target: 0 }, // ramp-down to 0 users
    ],
    thresholds: {
        http_req_duration: ['p(99)<500'] // 99% of requests must complete below 500 ms
    },
};

const BASE_URL = 'https://service.tinyurl.com'

export default function() {
    let url = data[Math.floor(Math.random() * data.length)];
    let postResponse = http.post(`${BASE_URL}/tinyurl`, url, { headers: {'Content-Type': 'application/json'} });
    check(postResponse, {
        '[POST] status was 200': (r) => r.status == 200,
        '[POST] url is same': (r) => r.json().fullUrl == url
    });

    sleep(Math.random() * 10);

    let code = postResponse.json().code
    let getResponse = http.get(`${BASE_URL}/tinyurl/${code}`);
    check(getResponse, {
        '[GET] status was 200': (r) => r.status == 200,
        '[GET] url is same': (r) => r.json().fullUrl == url
    });
}

