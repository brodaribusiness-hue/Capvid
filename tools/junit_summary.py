#!/usr/bin/env python3
"""Render Gradle's JUnit XML results as Markdown.

Gradle writes one XML file per test class under
``app/build/test-results/<task>/``. CI turns those into a table plus a
per-test list so the actual executed tests are visible on the pull request:
the runner log archive and the build artefacts are served from hosts that are
not reachable from every network, so numbers quoted from memory are worthless.

Usage: tools/junit_summary.py <test-results-dir> [<test-results-dir> ...]

Exits 0 even when tests failed - the caller decides what a failure means.
Exits 1 only if no results directory was found at all, which means the test
task never ran and there is nothing to report.
"""

import glob
import os
import sys
import xml.etree.ElementTree as ET


def collect(directories):
    """Return (rows, cases) read from every JUnit XML file under directories."""
    rows = []
    cases = []
    for directory in directories:
        for path in sorted(glob.glob(os.path.join(directory, "*.xml"))):
            try:
                root = ET.parse(path).getroot()
            except ET.ParseError as exc:
                rows.append((os.path.basename(path), 0, 0, 1, 0, 0.0))
                cases.append((os.path.basename(path), "ERROR",
                              "unparseable XML: %s" % exc))
                continue
            classname = root.get("name") or os.path.basename(path)
            rows.append((
                classname,
                int(root.get("tests", 0) or 0),
                int(root.get("failures", 0) or 0),
                int(root.get("errors", 0) or 0),
                int(root.get("skipped", 0) or 0),
                float(root.get("time", 0) or 0.0),
            ))
            for case in root.iter("testcase"):
                name = case.get("name") or "?"
                failure = case.find("failure")
                error = case.find("error")
                skipped = case.find("skipped")
                if failure is not None or error is not None:
                    node = failure if failure is not None else error
                    message = (node.get("message") or node.get("type")
                               or "no message").strip().splitlines()
                    verdict = "FAIL"
                    detail = message[0] if message else ""
                elif skipped is not None:
                    verdict = "SKIP"
                    detail = ""
                else:
                    verdict = "pass"
                    detail = ""
                cases.append((classname, verdict, name, detail))
    return rows, cases


def render(rows, cases):
    out = []
    totals = [0, 0, 0, 0]
    out.append("| test class | tests | failures | errors | skipped | seconds |")
    out.append("|---|---:|---:|---:|---:|---:|")
    for classname, tests, failures, errors, skipped, seconds in rows:
        totals[0] += tests
        totals[1] += failures
        totals[2] += errors
        totals[3] += skipped
        out.append("| `%s` | %d | %d | %d | %d | %.3f |"
                   % (classname, tests, failures, errors, skipped, seconds))
    out.append("| **total** | **%d** | **%d** | **%d** | **%d** | |"
               % tuple(totals))
    out.append("")
    out.append("<details><summary>individual tests</summary>")
    out.append("")
    current = None
    for entry in cases:
        classname, verdict, name = entry[0], entry[1], entry[2]
        detail = entry[3] if len(entry) > 3 else ""
        if classname != current:
            out.append("")
            out.append("**%s**" % classname)
            out.append("")
            current = classname
        line = "- %s `%s`" % (verdict, name)
        if detail:
            line += " - %s" % detail
        out.append(line)
    out.append("")
    out.append("</details>")
    return "\n".join(out), totals


def main(argv):
    directories = argv[1:]
    if not directories:
        print(__doc__.strip())
        return 2
    found = [d for d in directories if os.path.isdir(d)]
    if not found:
        print("No test results found. Looked in: %s" % ", ".join(directories))
        print("")
        print("The test task never produced XML output, so no test ran.")
        return 1
    rows, cases = collect(found)
    body, totals = render(rows, cases)
    print("Ran **%d** tests across %d classes - %d failed, %d errored, %d skipped."
          % (totals[0], len(rows), totals[1], totals[2], totals[3]))
    print("")
    print(body)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
