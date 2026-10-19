#!/usr/bin/env python3
"""
OpenTrack India - Train Tracking Screen Simulation
Train 16227 - MYS TLGP EXP (Mysuru Jn → Talguppa)
Data source: NTES (official Indian Railways)
"""

stops = [
    ("MYS", "MYSORE JN", "19:40", "19:40", "19:42", "19:42", "+2 min", "3", "board", "ACT", "NTES"),
    ("NHY", "NAGANAHALLI", "19:51", "19:52", "19:53", "19:54", "+1 min", "1", "", "ACT", "NTES"),
    ("PANP", "PANDAVAPURA", "20:02", "20:03", "20:07", "20:09", "+5 min", "1", "", "ACT", "NTES"),
    ("Y", "YELIYUR", "20:19", "20:20", "20:24", "20:26", "+5 min", "2", "", "ACT", "NTES"),
    ("MYA", "MANDYA", "20:27", "20:29", "20:27", "20:29", "0 min", "1", "Sch", "ACT", "NTES"),
    ("MAD", "MADDUR", "20:45", "20:46", "20:53", "20:54", "+8 min", "1", "", "ACT", "NTES"),
    ("CPT", "CHANNAPATNA", "21:00", "21:01", "21:11", "21:13", "+11 min", "2", "", "ACT", "NTES"),
    ("RMGM", "RAMANAGARAM", "21:11", "21:12", "21:26", "21:27", "+15 min", "1", "", "ACT", "NTES"),
    ("BID", "BIDADI", "21:22", "21:23", "21:22", "21:23", "0 min", "1", "", "ACT", "NTES"),
    ("HJL", "HEJJALA", "21:29", "21:30", "21:29", "21:30", "0 min", "1", "", "ACT", "NTES"),
    ("KGI", "KENGERI", "21:40", "21:41", "21:40", "21:41", "0 min", "2", "", "ACT", "NTES"),
    ("GNB", "JNANA BHARATI HALT", "21:45", "21:46", "21:45", "21:46", "0 min", "3", "", "ACT", "NTES"),
    ("NYH", "NAYANAHALLI", "21:49", "21:50", "21:49", "21:50", "0 min", "3", "", "ACT", "NTES"),
    ("SBC", "KSR BENGALURU", "23:05", "23:15", "23:05", "23:15", "0 min", "10", "", "ACT", "NTES"),
    ("MWM", "MALLESWARAM", "23:20", "23:21", "23:20", "23:21", "0 min", "2", "", "ACT", "NTES"),
    ("YPR", "YEShVANTPUR JN", "23:26", "23:28", "23:26", "23:28", "0 min", "3", "", "ACT", "NTES"),
    ("TK", "TUMKUR", "00:13", "00:15", "00:13", "00:15", "0 min", "2", "", "ACT", "NTES"),
    ("TTR", "TIPTUR", "01:05", "01:06", "01:05", "01:06", "0 min", "1", "", "ACT", "NTES"),
]

print("=" * 80)
print("  OpenTrack India 2.2.0 (build 2002000)")
print("  via NTES live running status · live data under a minute old")
print("  data as of 07:55:23 · fetched in 3.4s")
print("=" * 80)
print()
print("┌──────────────────────────────────────────────────────────────────────┐")
print("│  Current position                                          refresh   │")
print("│  At station                                              NTES erail │")
print("│  Tumkur (TK)                                          Auto WIMT   │")
print("│                                                                      │")
print("│  Position: Tumkur (TK)                                  4 min ago    │")
print("│  Confidence: high                                      Last reported │")
print("│  between Tumkur → Tiptur                                      TK     │")
print("│  0.0 km from Tumkur                                         00:13    │")
print("└──────────────────────────────────────────────────────────────────────┘")
print()
print("┌──────────────────────────────────────────────────────────────────────┐")
print("│ Timetable & actuals                                          search again │")
print("├──────────────────────────────────────────────────────────────────────┤")
print("│  Station (Code)        Scheduled        Actual        Delay   Pos   │")
print("│  Code    Name           Arr   Dep       Arr   Dep       min   Pltf  │")
print("├──────────────────────────────────────────────────────────────────────┤")

for i in range(len(stops)):
    s = stops[i]
    code = s[0]
    name = s[1]
    sch_arr = s[2]
    sch_dep = s[3]
    act_arr = s[4]
    act_dep = s[5]
    delay = s[6]
    platform = s[7]
    pos_marker = s[8]
    prov = s[9]
    source = s[10]

    pos_icon = "⬅" if pos_marker == "board" else ""
    prov_tag = f"[{prov}]"

    if act_arr == "—":
        arr_str = "—"
        dep_str = "—"
        delay_str = "—"
    else:
        arr_str = act_arr
        dep_str = act_dep
        delay_str = delay

    pos_str = pos_icon + " " if pos_marker == "board" else "  "

    print(f"│  {code:<6} {name:<14} {sch_arr:>5} {sch_dep:>5}   {arr_str:>5} {dep_str:>5}   {delay_str:<6} {platform:>6} {pos_str}{prov_tag:<8}")

print("├──────────────────────────────────────────────────────────────────────┤")
print("│  TLGP TALGUPPA · sched 07:50/Destination · actual — · not reported │")
print("└──────────────────────────────────────────────────────────────────────┘")
print()
print("  Source chips:  [Auto]  [NTES]  [erail]  [WIMT]  [RailYatri]  [Ixigo]  [ConfirmTkt]")
print()
print("  Train 16227 · MYS TLGP EXP · Mysuru Jn → Talguppa · 513 km · 12h 10m")
print("  Run days: Mon Tue Wed Thu Fri Sat Sun (daily)")
print()
print("  Source badge: via NTES live running status · live data under a minute old")
print("  Position: Train departed Tumkur, heading to Tiptur · 4 min ago")
print()
print("=" * 80)
