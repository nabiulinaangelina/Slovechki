#!/usr/bin/env python3
"""Merge Quizlet CSV/TSV exports into the study vocabulary by unit and term."""
from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import unicodedata
from pathlib import Path


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFKC", value).casefold().strip()
    value = re.sub(r"\s*\((?:n|v|adj|adv)\)\s*$", "", value)
    value = value.replace("&", " and ")
    return "".join(ch for ch in value if ch.isalnum())


def unit_from_name(path: Path) -> int | None:
    match = re.search(r"unit[\s_-]*0?(\d{1,2})", path.stem, re.I)
    return int(match.group(1)) if match else None


def lookup_words(words: list[dict]) -> dict[tuple[int, str], dict]:
    lookup: dict[tuple[int, str], dict] = {}
    for word in words:
        unit = word["unit"]
        term = word["term"]
        aliases = {term}
        aliases.add(re.sub(r"\s*\([^)]*\)", "", term).strip())
        acronym = re.search(r"\(([A-Z][A-Za-z0-9-]{1,})\)\s*$", term)
        if acronym:
            aliases.add(acronym.group(1))
        aliases.add(re.sub(r"\s+models?$", "", term, flags=re.I))
        aliases.add(re.sub(r"\s+systems?$", "", term, flags=re.I))
        aliases.add(re.sub(r"\s+paradigm$", "", term, flags=re.I))
        if normalize(term) == normalize("Just-in-Time compiler"):
            aliases.add("JIT")
        if normalize(term) == normalize("Fell (through)"):
            aliases.add("fall through")
        if normalize(term) == normalize("Number-crunching tasks"):
            aliases.add("Number-crunching (tasks)")
        if normalize(term) == normalize("Eradication"):
            aliases.add("Eradication of death")
        expanded_aliases = set(aliases)
        for alias in aliases:
            bare = re.sub(r"\s*\([^)]*\)", "", alias).strip()
            expanded_aliases.add(re.sub(r"\s+computer$", "", bare, flags=re.I))
            final = re.search(r"(\S+)$", bare)
            if final:
                last = final.group(1)
                if not last.isupper() and last.casefold().endswith("ies") and len(last) > 3:
                    expanded_aliases.add(bare[:final.start(1)] + last[:-3] + "y")
                elif not last.isupper() and last.casefold().endswith("s") and not last.casefold().endswith(("ss", "us", "is")):
                    expanded_aliases.add(bare[:final.start(1)] + last[:-1])
        for alias in expanded_aliases:
            lookup[(unit, normalize(alias))] = word
        # The course uses British -ise in the glossary; some Quizlet cards use -ize.
        norm = normalize(term)
        if "realised" in norm:
            lookup[(unit, norm.replace("realised", "realized"))] = word
        if "realized" in norm:
            lookup[(unit, norm.replace("realized", "realised"))] = word
    return lookup


def read_cards(path: Path) -> list[tuple[str, str]]:
    text = path.read_text(encoding="utf-8-sig").strip()
    if not text:
        return []
    sample = "\n".join(text.splitlines()[:12])
    try:
        dialect = csv.Sniffer().sniff(sample, delimiters="\t,;|")
    except csv.Error:
        dialect = csv.excel_tab if "\t" in sample else csv.excel
    rows = [row for row in csv.reader(text.splitlines(), dialect) if len(row) >= 2]
    if not rows:
        return []
    header = [normalize(cell) for cell in rows[0]]
    term_names = {"term", "word", "question", "front", "слово", "термин"}
    translation_names = {"definition", "answer", "translation", "back", "перевод", "значение"}
    term_col = next((i for i, name in enumerate(header) if name in term_names), None)
    translation_col = next((i for i, name in enumerate(header) if name in translation_names), None)
    if term_col is not None and translation_col is not None:
        rows = rows[1:]
    else:
        term_col, translation_col = 0, 1
    cards = []
    for row in rows:
        if max(term_col, translation_col) >= len(row):
            continue
        term = re.sub(r"\s+", " ", row[term_col]).strip()
        translation = re.sub(r"\s+", " ", row[translation_col]).strip()
        if term and translation:
            cards.append((term, translation))
    return cards


def write_csv(path: Path, payload: dict) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["unit", "unitTitle", "term", "partOfSpeech", "translation"])
        writer.writeheader()
        for word in payload["words"]:
            unit = payload["units"][word["unit"] - 1]
            writer.writerow({"unit": word["unit"], "unitTitle": unit["title"], "term": word["term"],
                             "partOfSpeech": word["partOfSpeech"], "translation": word["translation"]})


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("export_dir", type=Path, help="Directory with unit-01.csv/.tsv/.txt exports")
    parser.add_argument("--json", type=Path, default=Path("app/src/main/assets/vocabulary.json"))
    parser.add_argument("--csv", type=Path, default=Path("data/vocabulary.csv"))
    parser.add_argument("--overrides", type=Path, default=Path("data/translation_overrides.json"))
    args = parser.parse_args()

    payload = json.loads(args.json.read_text(encoding="utf-8"))
    words_by_key = lookup_words(payload["words"])
    files = sorted(path for path in args.export_dir.iterdir() if path.suffix.lower() in {".csv", ".tsv", ".txt"})
    if not files:
        print(f"No CSV/TSV/TXT export files in {args.export_dir}", file=sys.stderr)
        return 2

    matched_by_unit: dict[int, int] = {}
    extra_cards: dict[int, list[tuple[str, str]]] = {}
    extra_added: dict[int, int] = {}
    unknown_files: list[str] = []
    conflicts: list[str] = []
    seen: set[tuple[int, str]] = set()
    for path in files:
        unit = unit_from_name(path)
        if unit is None or unit not in range(1, 12):
            unknown_files.append(path.name)
            continue
        cards = read_cards(path)
        for term, translation in cards:
            key = (unit, normalize(term))
            word = words_by_key.get(key)
            if word is None:
                without_parentheticals = re.sub(r"\s*\([^)]*\)", "", term).strip()
                word = words_by_key.get((unit, normalize(without_parentheticals)))
            if word is None:
                abbreviations = re.findall(r"\(([A-Z][A-Z0-9-]{1,})\)", term)
                word = next((words_by_key.get((unit, normalize(abbreviation))) for abbreviation in abbreviations
                             if words_by_key.get((unit, normalize(abbreviation)))), None)
            if word is None:
                # Sets sometimes write an abbreviation before its expansion, e.g. "URI (Uniform Resource Identifier)".
                leading = re.match(r"\s*([A-Z][A-Z0-9-]{1,})\b", term)
                if leading:
                    word = words_by_key.get((unit, normalize(leading.group(1))))
            if word is None:
                extra_cards.setdefault(unit, []).append((term, translation))
                continue
            if key in seen and word["translation"] and word["translation"] != translation:
                conflicts.append(f"Unit {unit}: {word['term']} ({word['translation']} / {translation})")
            word["translation"] = translation
            seen.add(key)
            matched_by_unit[unit] = matched_by_unit.get(unit, 0) + 1

    # Keep set cards that are useful but are not headwords in the textbook glossary.
    # This also means a later import from a freshly extracted glossary restores them.
    for unit, cards in extra_cards.items():
        existing = {normalize(word["term"]) for word in payload["words"] if word["unit"] == unit}
        unit_words = [word for word in payload["words"] if word["unit"] == unit]
        for term, translation in cards:
            key = normalize(term)
            if key in existing:
                continue
            index = len([word for word in unit_words if word["id"].startswith(f"u{unit:02d}-extra-")]) + 1
            unit_words.append({
                "id": f"u{unit:02d}-extra-{index:03d}",
                "unit": unit,
                "term": term,
                "partOfSpeech": "",
                "translation": translation,
            })
            existing.add(key)
            extra_added[unit] = extra_added.get(unit, 0) + 1
        payload["words"].extend(word for word in unit_words if word["id"].startswith(f"u{unit:02d}-extra-") and word not in payload["words"])
        payload["units"][unit - 1]["count"] = sum(1 for word in payload["words"] if word["unit"] == unit)

    if args.overrides.exists():
        overrides = json.loads(args.overrides.read_text(encoding="utf-8"))
        words_by_exact_term = {(word["unit"], normalize(word["term"])): word for word in payload["words"]}
        for override in overrides:
            key = (override["unit"], normalize(override["term"]))
            word = words_by_exact_term.get(key)
            if word is None:
                raise SystemExit(f"Override does not match a glossary term: Unit {key[0]} {override['term']}")
            word["translation"] = override["translation"]

    args.json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.csv.exists():
        write_csv(args.csv, payload)

    for unit in payload["units"]:
        translated = sum(1 for word in payload["words"] if word["unit"] == unit["id"] and word["translation"].strip())
        print(f"Unit {unit['id']:02d}: {translated}/{unit['count']} translations present")
    added_total = sum(extra_added.values())
    if added_total:
        summary = ", ".join(f"Unit {unit:02d}: {count}" for unit, count in sorted(extra_added.items()))
        print(f"\nAdded {added_total} cards outside the textbook glossary ({summary}).")
    if unknown_files:
        print("\nFiles whose unit number could not be read from the filename:")
        for item in unknown_files:
            print(f"  - {item}")
    if conflicts:
        print("\nConflicting duplicate cards (last card kept):")
        for item in conflicts:
            print(f"  - {item}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
