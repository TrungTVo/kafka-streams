# Windowed aggregation example (Kafka Streams)

This note shows a **simple event stream** and how the **aggregated output** changes depending on the **window size**.

---

## 1) Input events timeline

### Events (in arrival order)

| # | Time offset | Key  | Price |
|---:|:-----------|:-----|------:|
| 1 | `T0`        | `HDTV` | 2000.00 |
| 2 | `T0+10s`    | `HDTV` | 1999.23 |
| 3 | `T0+20s`    | `ABCD` | 4500.00 |
| 4 | `T0+30s`    | `ABCD` | 1333.98 |
| 5 | `T0+40s`    | `HDTV` | 5000.98 |

### Visual timeline

```text
time →
T0            T0+10s          T0+20s         T0+30s         T0+40s         T0+50s
|--------------|--------------|--------------|--------------|--------------|
#1 HDTV        #2 HDTV        #3 ABCD        #4 ABCD        #5 HDTV
2000.00        1999.23        4500.00        1333.98        5000.98
```

---

## 2) Windowing examples

> Assumption: **suppress is disabled**  
> → the application emits **intermediate updates** as new records arrive in the window.

---

### A) Window size = 18 seconds

```text
T0                         T0+18                       T0+36                       T0+54
|---------- W1 ------------|----------- W2 ------------|----------- W3 ------------|
```

#### Expected output (intermediate updates)

**Window 1 (`[T0, T0+18)`):**
- Aggregated record — key: `HDTV`, value(price): `2000.0`
- Aggregated record — key: `HDTV`, value(price): `3999.23`

**Window 2 (`[T0+18, T0+36)`):**
- Aggregated record — key: `ABCD`, value(price): `4500.0`
- Aggregated record — key: `ABCD`, value(price): `5833.98`

**Window 3 (`[T0+36, T0+54)`):**
- Aggregated record — key: `HDTV`, value(price): `5000.98`

> Note: you might only see **Window 3** output *after* another record arrives **past `T0+54s`** (depending on your stream-time advancement).

---

### B) Window size = 25 seconds

```text
T0                                     T0+25                                T0+50
|----------------- W1 -----------------|---------------- W2 ----------------|
```

#### Expected output (intermediate updates)

**Window 1 (`[T0, T0+25)`):**
- Aggregated record — key: `HDTV`, value(price): `2000.0`
- Aggregated record — key: `HDTV`, value(price): `3999.23`
- Aggregated record — key: `ABCD`, value(price): `4500.0`

**Window 2 (`[T0+25, T0+50)`):**
- Aggregated record — key: `ABCD`, value(price): `1333.98`
- Aggregated record — key: `HDTV`, value(price): `5000.98`

---

## Quick note on `suppress`

If you **enable suppress**, Kafka Streams will typically emit **only the final aggregate per key per window** *after the window closes* (subject to grace period / stream-time / commit behavior).
