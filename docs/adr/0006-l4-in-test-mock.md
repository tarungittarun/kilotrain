# ADR 0006 — L4 mock: in-test local server (deviation from 10.0.2.2)

Date: 2026-10-11 · Status: accepted · Approver: owner (brief §10.1 L4)

Context: L4 wants "emulator + LOCAL MOCK at 10.0.2.2 serving captured fixtures". That requires a
host-side server process and port plumbing per runner. Instrumented tests already run app + test
in one process, so the same proof (fetch→parse→store→UI with zero railway egress) is achieved by
an in-test ServerSocket on 127.0.0.1 that serves committed fixture bytes.

Decision: L4 uses an in-test loopback mock serving poc/fixtures-derived bytes; the emulator
article is L3/L5. /dev/kvm detection stays: the instrumented job reports "untested, with a
reason" when KVM is missing — never a fake pass.

Consequences: no host process to babysit; fixtures are committed (small, redacted, attributed).
Reversibility: high. Revisit trigger: a test that needs host-side state or a second process.
