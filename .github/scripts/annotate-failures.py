"""Turns build failures into GitHub annotations, which anyone can read.

Job logs of a public repository need admin rights to download through the API,
but annotations are public. This prints one ::error:: line per failed JUnit
test (from build/test-results) and the key lines of the saved Gradle logs.
"""
import glob
import os
import xml.etree.ElementTree as ElementTree


def clean(text, limit=600):
    text = " ".join((text or "").split())
    return text[:limit].replace("%", "%25").replace("\r", "").replace("\n", " ")


count = 0
for path in glob.glob("**/build/test-results/**/*.xml", recursive=True):
    try:
        root = ElementTree.parse(path).getroot()
    except ElementTree.ParseError:
        continue
    for case in root.iter("testcase"):
        for problem in list(case.findall("failure")) + list(case.findall("error")):
            name = case.get("classname", "?") + "." + case.get("name", "?")
            message = problem.get("message") or problem.text or ""
            print("::error title=Test failed: " + clean(name, 200) + "::" + clean(message))
            count += 1
            if count >= 25:
                break

for log in glob.glob("*.gradle.log"):
    with open(log, encoding="utf-8", errors="replace") as handle:
        lines = handle.read().splitlines()
    keys = [line for line in lines if "What went wrong" in line or "FAILED" in line
            or "error:" in line or "Exception" in line]
    for line in keys[:15]:
        print("::error title=" + os.path.basename(log) + "::" + clean(line))
    for line in lines[-12:]:
        print("::warning title=tail " + os.path.basename(log) + "::" + clean(line))

print("annotated", count, "test failures")
