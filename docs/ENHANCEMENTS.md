# ENHANCEMENTS.md - Making sources try harder (mimic real requests)

## Changes
- ConfirmTkt/RailYatri/Ixigo sources now try multiple candidate endpoints + multiple header sets
- Accept configurable endpoint via param
- Try common combinations; fall back to next on failure
- Keep parsers flexible and shape-guarded

## Why
Web probes show HTML-first; APIs may require specific headers/paths. Trying multiple realistic combinations increases chance of hitting correct endpoint if it exists. Still capture-first - if all fail, we need device-captured endpoint.
