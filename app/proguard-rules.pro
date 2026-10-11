# R8 + resource shrinking are exercised by the release build in CI (9.4).
# No reflection-based serialization is used; keep rules explicit and minimal.
-dontwarn org.jetbrains.annotations.**
