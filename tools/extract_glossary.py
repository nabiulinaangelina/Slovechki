#!/usr/bin/env python3
"""Extract the glossary terms and unit boundaries from the course PDF."""
from __future__ import annotations
import argparse
import csv
import json
import re
import subprocess
from pathlib import Path

UNIT_URLS = {
    1: "https://quizlet.com/820231498/computer-networks-flash-cards/",
    2: "https://quizlet.com/1047050796/the-world-wide-web-flash-cards/",
    3: "https://quizlet.com/1057811602/websites-cloud-computing-flash-cards/",
    4: "https://quizlet.com/1049941332/computer-malwares-flash-cards/",
    5: "https://quizlet.com/1052691491/unit-5-data-security-flash-cards/",
    6: "https://quizlet.com/1052023992/unit-6-programming-languages-flash-cards/",
    7: "https://quizlet.com/1051355218/java-flash-cards/",
    8: "https://quizlet.com/1053385789/ai-flash-cards/",
    9: "https://quizlet.com/1056143329/unit-9-future-technologies-flash-cards/",
    10: "https://quizlet.com/1056183842/unit-10-it-careers-flash-cards/",
    11: "https://quizlet.com/1056853637/unit-11-my-speciality-flash-cards/",
}
ENTRY = re.compile(r"^\s*(.+?)\s*\((n|v|adj|adv)\)\s+—", re.I)
UNIT = re.compile(r"(?m)^\s*UNIT\s+(\d+)\.\s*(.+?)\s*$")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("pdf", type=Path)
    parser.add_argument("--json", type=Path, required=True)
    parser.add_argument("--csv", type=Path, required=True)
    args = parser.parse_args()
    text = subprocess.check_output(["pdftotext", "-f", "332", "-l", "358", "-layout", str(args.pdf), "-"] , text=True)
    units = []
    matches = list(UNIT.finditer(text))
    for i, match in enumerate(matches):
        number = int(match.group(1))
        title = match.group(2).strip()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        body = text[match.end():end]
        words = []
        for line in body.splitlines():
            found = ENTRY.match(line)
            if not found:
                continue
            term = re.sub(r"\s+", " ", found.group(1).strip())
            # This printed entry is a merged headword; the glossary has a separate
            # "Prosthesis" entry later on the same page, so this entry is Dehumanization.
            if term.casefold() == "prosthesis dehumanization":
                term = "Dehumanization"
            # The definition for Footer wraps into the next entry on the same line.
            if term.casefold() == "copyright notices, or header":
                term = "Header"
            words.append({"id": f"u{number:02d}-{len(words)+1:03d}", "unit": number,
                          "term": term, "partOfSpeech": found.group(2).lower(),
                          "translation": ""})
        units.append({"id": number, "title": title, "sourceUrl": UNIT_URLS[number], "count": len(words)})
        if len(units) == 1:
            all_words = words
        else:
            all_words.extend(words)
    if len(units) != 11 or len(all_words) != 481:
        raise SystemExit(f"Expected 11 units / 481 terms, found {len(units)} / {len(all_words)}")
    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.csv.parent.mkdir(parents=True, exist_ok=True)
    payload = {"version": 1, "units": units, "words": all_words}
    args.json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with args.csv.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["unit", "unitTitle", "term", "partOfSpeech", "translation", "sourceUrl"])
        writer.writeheader()
        for word in all_words:
            writer.writerow({"unit": word["unit"], "unitTitle": units[word["unit"]-1]["title"],
                             "term": word["term"], "partOfSpeech": word["partOfSpeech"],
                             "translation": "", "sourceUrl": UNIT_URLS[word["unit"]]})
    print("Units:", ", ".join(f"{u['id']}={u['count']}" for u in units))
    print("Total terms:", len(all_words))

if __name__ == "__main__":
    main()
