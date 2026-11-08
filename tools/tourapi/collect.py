#!/usr/bin/env python3
"""TourAPI(KorService2) 데이터 수집 스크립트 — 표준 라이브러리만 사용.

키: 환경변수 TOUR_API_KEY 또는 프로젝트 루트 .env 의 TOUR_API_KEY (공공데이터포털 '일반 인증키(Decoding)')

  python3 tools/tourapi/collect.py codes            # 법정동 지역 + 분류체계 코드 (호출 몇 번)
  python3 tools/tourapi/collect.py lists            # 관광지·문화시설·음식점·숙박 목록 + 축제 목록
  python3 tools/tourapi/collect.py intro --limit 900  # 운영시간·쉬는날·행사시간 (항목당 1회, 이어받기)
  python3 tools/tourapi/collect.py export           # ERD 컬럼에 맞춘 CSV 생성

결과는 tools/tourapi/data/ (git 제외).
"""
import argparse
import csv
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, timedelta
from pathlib import Path

BASE = "https://apis.data.go.kr/B551011/KorService2"
ROOT = Path(__file__).resolve().parents[2]
DATA = Path(__file__).resolve().parent / "data"
RAW = DATA / "raw"
INTRO = RAW / "intro"

# contenttypeid → ERD contents.content_type
TYPES = {"12": "ATTRACTION", "14": "ATTRACTION", "15": "FESTIVAL", "39": "RESTAURANT", "32": "LODGING"}
LIST_TYPES = ["12", "14", "39", "32"]  # 15(축제)는 searchFestival2로 받는다


class QuotaExceeded(Exception):
    pass


def api_key() -> str:
    key = os.environ.get("TOUR_API_KEY")
    env = ROOT / ".env"
    if not key and env.exists():
        for line in env.read_text(encoding="utf-8").splitlines():
            if line.strip().startswith("TOUR_API_KEY="):
                key = line.split("=", 1)[1].strip().strip('"')
    if not key:
        sys.exit("TOUR_API_KEY가 없습니다 (.env 또는 환경변수)")
    return key


CALLS = 0


def call(op: str, **params) -> dict:
    """한 번 호출하고 body 를 돌려준다. 오류 코드면 예외."""
    global CALLS
    q = {"serviceKey": api_key(), "MobileOS": "ETC", "MobileApp": "EDGL", "_type": "json", **params}
    url = f"{BASE}/{op}?{urllib.parse.urlencode(q)}"
    for attempt in range(3):
        try:
            CALLS += 1
            with urllib.request.urlopen(url, timeout=30) as r:
                text = r.read().decode("utf-8")
            break
        except (urllib.error.URLError, TimeoutError) as e:
            if attempt == 2:
                raise
            time.sleep(2 * (attempt + 1))
    if text.lstrip().startswith("<"):  # 키 오류·한도 초과는 XML 로 온다
        if "LIMITED_NUMBER" in text:
            raise QuotaExceeded(text[:300])
        raise RuntimeError(f"{op} XML 오류: {text[:300]}")
    data = json.loads(text)
    if "response" not in data:  # 일부 오류는 {"resultCode":..} 형태
        msg = json.dumps(data, ensure_ascii=False)[:300]
        if "22" == str(data.get("resultCode")) or "LIMITED" in msg:
            raise QuotaExceeded(msg)
        raise RuntimeError(f"{op} 오류: {msg}")
    header = data["response"]["header"]
    if header.get("resultCode") != "0000":
        raise RuntimeError(f"{op} 오류: {header}")
    return data["response"]["body"]


def items(body: dict) -> list:
    it = body.get("items")
    if not it:  # 빈 결과는 "" 로 온다
        return []
    it = it.get("item", [])
    return it if isinstance(it, list) else [it]


def fetch_all(op: str, rows: int = 1000, **params) -> list:
    out, page = [], 1
    while True:
        body = call(op, numOfRows=rows, pageNo=page, **params)
        got = items(body)
        out += got
        total = int(body.get("totalCount", 0))
        print(f"  {op} {params} page {page}: {len(out)}/{total}")
        if not got or len(out) >= total:
            return out
        page += 1


def save(name: str, obj) -> None:
    RAW.mkdir(parents=True, exist_ok=True)
    (RAW / name).write_text(json.dumps(obj, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"저장: {RAW / name} ({len(obj)}건)")


def load(name: str) -> list:
    p = RAW / name
    return json.loads(p.read_text(encoding="utf-8")) if p.exists() else []


def cmd_codes(_):
    save("ldong.json", fetch_all("ldongCode2", lDongListYn="Y"))
    save("lcls.json", fetch_all("lclsSystmCode2", lclsSystmListYn="Y"))


def cmd_lists(args):
    for t in LIST_TYPES:
        save(f"list_{t}.json", fetch_all("areaBasedList2", contentTypeId=t, arrange="C"))
    start = (date.today() - timedelta(days=args.festival_days_back)).strftime("%Y%m%d")
    save("festival.json", fetch_all("searchFestival2", eventStartDate=start, arrange="C"))


def cmd_intro(args):
    INTRO.mkdir(parents=True, exist_ok=True)
    targets = []
    for name in ["festival.json", "list_12.json", "list_14.json"]:
        targets += [(i["contentid"], i["contenttypeid"]) for i in load(name)]
    todo = [(c, t) for c, t in targets if not (INTRO / f"{c}.json").exists()]
    print(f"intro 남은 항목 {len(todo)} / 전체 {len(targets)}, 이번 실행 최대 {args.limit}")
    done = 0
    try:
        for cid, tid in todo[: args.limit]:
            got = items(call("detailIntro2", contentId=cid, contentTypeId=tid))
            (INTRO / f"{cid}.json").write_text(json.dumps(got[0] if got else {}, ensure_ascii=False), encoding="utf-8")
            done += 1
            time.sleep(args.sleep)
    except QuotaExceeded as e:
        print(f"일일 한도 초과로 중단: {e}")
    print(f"이번 실행 {done}건 저장, 남은 항목 {len(todo) - done}")


def ymd(s):
    s = (s or "").strip()
    return f"{s[:4]}-{s[4:6]}-{s[6:8]}" if len(s) >= 8 else ""


def ts(s):
    s = (s or "").strip()
    return f"{s[:4]}-{s[4:6]}-{s[6:8]} {s[8:10]}:{s[10:12]}:{s[12:14]}" if len(s) >= 14 else ""


def write_csv(name, header, rows):
    DATA.mkdir(parents=True, exist_ok=True)
    with open(DATA / name, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(rows)
    print(f"CSV: {DATA / name} ({len(rows)}행)")


def cmd_export(_):
    # regions: 시·도 + 시·군·구 (법정동 코드 기준; ERD의 area_code 칼럼에 lDongRegnCd 를 넣는다)
    ldong = load("ldong.json")
    sido, sgg = {}, []
    for r in ldong:
        sido.setdefault(r["lDongRegnCd"], r["lDongRegnNm"])
        if r.get("lDongSignguCd") and r["lDongSignguCd"] != r["lDongRegnCd"]:  # 세종은 시군구 없이 자기 코드만 온다
            sgg.append(r)
    regions = [[cd, "", nm, ""] for cd, nm in sorted(sido.items())]
    regions += [[r["lDongRegnCd"], r["lDongSignguCd"], r["lDongSignguNm"], r["lDongRegnCd"]] for r in sgg]
    write_csv("regions.csv", ["area_code", "sigungu_code", "name", "parent_area_code"], regions)

    lcls = load("lcls.json")
    write_csv("lcls_codes.csv", ["lcls1_cd", "lcls1_nm", "lcls2_cd", "lcls2_nm", "lcls3_cd", "lcls3_nm"],
              [[r.get("lclsSystm1Cd"), r.get("lclsSystm1Nm"), r.get("lclsSystm2Cd"), r.get("lclsSystm2Nm"),
                r.get("lclsSystm3Cd"), r.get("lclsSystm3Nm")] for r in lcls])

    io_map = {}
    io_csv = Path(__file__).resolve().parent / "indoor_outdoor.csv"
    if io_csv.exists():
        with open(io_csv, encoding="utf-8-sig") as f:
            io_map = {r["lcls3_cd"]: r["io"] for r in csv.DictReader(f)}

    rows, skipped = [], 0
    for name in [f"list_{t}.json" for t in LIST_TYPES] + ["festival.json"]:
        for i in load(name):
            ctype = TYPES.get(i.get("contenttypeid"), "ATTRACTION")
            if not i.get("mapx") or not i.get("mapy") or (ctype == "FESTIVAL" and not (i.get("eventstartdate") and i.get("eventenddate"))):
                skipped += 1  # 좌표 없음 / 축제 날짜 누락 → 제외 (006-01)
                continue
            rows.append([
                i["contentid"], ctype, i.get("contenttypeid"), i.get("title", "").strip(),
                (i.get("addr1", "") + " " + i.get("addr2", "")).strip(), i["mapy"], i["mapx"],
                i.get("lDongRegnCd", ""), i.get("lDongSignguCd", ""),
                i.get("lclsSystm1", ""), i.get("lclsSystm2", ""), i.get("lclsSystm3", ""),
                i.get("firstimage", ""), ymd(i.get("eventstartdate")), ymd(i.get("eventenddate")),
                ts(i.get("modifiedtime")), i.get("tel", ""), io_map.get(i.get("lclsSystm3", ""), ""),
            ])
    write_csv("contents.csv", ["content_id", "content_type", "tour_content_type_id", "name", "address", "latitude",
                               "longitude", "ldong_regn_cd", "ldong_signgu_cd", "lcls1", "lcls2", "tour_cat_code",
                               "thumbnail_url", "event_start_date", "event_end_date", "tour_modified_at", "tel", "io"], rows)
    print(f"제외 {skipped}건 (좌표 없음/축제 날짜 누락)")

    details = []
    for p in sorted(INTRO.glob("*.json")) if INTRO.exists() else []:
        d = json.loads(p.read_text(encoding="utf-8"))
        if not d:
            continue
        details.append([
            p.stem,
            d.get("usetime") or d.get("playtime") or d.get("usetimeculture") or "",  # 운영·공연 시간
            d.get("restdate") or d.get("restdateculture") or "",                       # 쉬는 날
            d.get("eventstarttime", ""),
            d.get("usefee") or d.get("usetimefestival") or "",                         # 요금
            d.get("spendtime") or d.get("spendtimefestival") or "",
            d.get("agelimit", ""), d.get("sponsor1", ""),
            d.get("infocenter") or d.get("sponsor1tel") or d.get("infocenterculture") or "",
            d.get("eventplace", ""),
        ])
    write_csv("content_details.csv", ["content_id", "operating_hours", "rest_date", "event_start_time", "fee",
                                      "duration", "age_limit", "host", "contact", "event_place"], details)


def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("codes")
    pl = sub.add_parser("lists")
    pl.add_argument("--festival-days-back", type=int, default=365)
    pi = sub.add_parser("intro")
    pi.add_argument("--limit", type=int, default=900)
    pi.add_argument("--sleep", type=float, default=0.1)
    sub.add_parser("export")
    args = p.parse_args()
    try:
        {"codes": cmd_codes, "lists": cmd_lists, "intro": cmd_intro, "export": cmd_export}[args.cmd](args)
    finally:
        print(f"API 호출 수: {CALLS}")


if __name__ == "__main__":
    main()
