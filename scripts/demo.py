"""Exercise the real local HTTP API; optionally verify receipts after a restart."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import build_opener, ProxyHandler, Request
import uuid


def request(base, path, method="GET", body=None, key=None):
    headers = {"Content-Type": "application/json"}
    if key is not None:
        headers["Idempotency-Key"] = key
    data = None if body is None else json.dumps(body).encode()
    # Loopback demo requests must not depend on a machine's HTTP proxy settings.
    opener = build_opener(ProxyHandler({}))
    try:
        response = opener.open(Request(base + path, data, headers, method=method), timeout=10)
    except HTTPError as error:
        response = error
    with response:
        return response.status, json.load(response)


def expect(result, status, *, code=None, state=None):
    actual, payload = result
    if actual != status:
        raise AssertionError(f"Expected HTTP{status}, got HTTP{actual}: {payload}")
    if code is not None and payload.get("code") != code:
        raise AssertionError(f"Expected error code{code}: {payload}")
    if state is not None and payload.get("status") != state:
        raise AssertionError(f"Expected payment status{state}: {payload}")
    return payload


def confirm(base, identifier, method):
    return request(base, f"/{identifier}/confirm", "POST", {"paymentMethodId": method})


def verify_saved(base, proof):
    for saved in proof["receipts"]:
        receipt = saved["receipt"]
        loaded = expect(request(base, "/" + receipt["id"]), 200)
        replay = expect(confirm(base, receipt["id"], saved["method"]), 200)
        if loaded != receipt or replay != receipt:
            raise AssertionError("Persisted receipt changed after application restart")
    return {"verifiedPersistedReceipts": len(proof["receipts"])}


def demo(base):
    expect(request(base, "/health"), 200, state="UP")
    marker = str(uuid.uuid4())
    payment = {"amount": 12.50, "currency": "USD", "customerEmail": marker + "@example.com",
               "customerId": "demo-customer", "description": "Simulated payment"}
    key = "demo-" + marker
    with ThreadPoolExecutor(max_workers=4) as pool:
        created = list(pool.map(lambda _: expect(request(base, "", "POST", payment, key), 201,
                                                state="PENDING"), range(4)))
    identifiers = {item["id"] for item in created}
    if len(identifiers) != 1:
        raise AssertionError("Concurrent same-key creates returned different payments")
    identifier = created[0]["id"]
    normalized = dict(payment, currency="usd", customerEmail=payment["customerEmail"].upper())
    if expect(request(base, "", "POST", normalized, key), 201)["id"] != identifier:
        raise AssertionError("Normalized retry did not reuse payment")
    expect(request(base, "", "POST", dict(payment, amount=13), key), 409, code="idempotency_conflict")
    with ThreadPoolExecutor(max_workers=4) as pool:
        completed = list(pool.map(lambda _: expect(confirm(base, identifier, "pm_success"), 200,
                                                  state="COMPLETED"), range(4)))
    if any(receipt != completed[0] for receipt in completed):
        raise AssertionError("Concurrent confirmations returned different receipts")
    expect(confirm(base, identifier, "pm_decline"), 409, code="confirmation_conflict")
    if expect(request(base, "", "POST", payment, key), 201) != completed[0]:
        raise AssertionError("Create replay did not return the current persisted payment")

    declined_id = expect(request(base, "", "POST", payment, key + "-decline"), 201)["id"]
    declined = expect(confirm(base, declined_id, "pm_decline"), 200, state="FAILED")
    if expect(confirm(base, declined_id, "pm_decline"), 200) != declined:
        raise AssertionError("Declined confirmation replay changed its receipt")
    if declined["completedAt"] is not None or not declined["failureReason"]:
        raise AssertionError("Declined receipt has incoherent completion details")

    recovered_id = expect(request(base, "", "POST", payment, key + "-recovery"), 201)["id"]
    expect(confirm(base, recovered_id, "pm_unavailable"), 503, code="provider_unavailable")
    pending = expect(request(base, "/" + recovered_id), 200, state="PENDING")
    if pending["completedAt"] is not None or pending["failureReason"] is not None:
        raise AssertionError("Unavailability changed pending outcome details")
    recovered = expect(confirm(base, recovered_id, "pm_success"), 200, state="COMPLETED")
    expect(request(base, "/" + str(uuid.uuid4())), 404, code="payment_not_found")
    expect(request(base, "/not-a-uuid"), 400, code="invalid_request")
    expect(request(base, "", "POST", dict(payment, amount=0.001), key + "-invalid"), 400,
           code="invalid_request")
    expect(request(base, "", "POST", dict(payment, currency="ZZZ"), key + "-invalid"), 400,
           code="invalid_request")
    receipts = [{"method": "pm_success", "receipt": completed[0]},
                {"method": "pm_decline", "receipt": declined},
                {"method": "pm_success", "receipt": recovered}]
    proof = {"concurrentCreateRequests": 4, "distinctCreatedPayments": 1,
             "concurrentConfirmationRequests": 4, "identicalCompletionReceipts": True,
             "payloadConflictHttpStatus": 409, "methodConflictHttpStatus": 409,
             "unavailableHttpStatus": 503, "recoveryStatus": "COMPLETED", "receipts": receipts}
    verify_saved(base, proof)
    return proof


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080/api/v1/payments")
    parser.add_argument("--output", type=Path, help="Save JSON proof for restart verification")
    parser.add_argument("--verify-receipts", type=Path, help="Verify saved receipts without new payments")
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    proof = (verify_saved(base, json.loads(args.verify_receipts.read_text(encoding="utf-8")))
             if args.verify_receipts else demo(base))
    rendered = json.dumps(proof, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    main()
