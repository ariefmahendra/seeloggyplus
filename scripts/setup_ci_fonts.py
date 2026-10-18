import ctypes
import hashlib
import io
import os
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


def configure(root):
    fonts = root / "fonts"
    fonts.mkdir(parents=True, exist_ok=True)
    cache = root / "cache"
    cache.mkdir(exist_ok=True)
    config = ET.Element("fontconfig")
    ET.SubElement(config, "dir").text = str(fonts)
    ET.SubElement(config, "cachedir").text = str(cache)
    for generic, family in [("sans-serif", "DejaVu Sans"), ("serif", "DejaVu Serif"), ("monospace", "DejaVu Sans Mono")]:
        alias = ET.SubElement(config, "alias")
        ET.SubElement(alias, "family").text = generic
        ET.SubElement(ET.SubElement(alias, "prefer"), "family").text = family
    path = root / "fonts.conf"
    ET.ElementTree(config).write(path, encoding="utf-8", xml_declaration=True)
    return fonts, path


if __name__ == "__main__":
    print(subprocess.check_output(["cat", "/etc/os-release"], text=True))
    ctypes.CDLL("libfontconfig.so.1")
    root = Path(os.environ["RUNNER_TEMP"]) / "seeloggy-fonts"
    fonts, config = configure(root)
    url = "https://github.com/dejavu-fonts/dejavu-fonts/releases/download/version_2_37/dejavu-fonts-ttf-2.37.zip"
    with urllib.request.urlopen(url, timeout=60) as response:
        payload = response.read()
    if hashlib.sha256(payload).hexdigest() != "7576310b219e04159d35ff61dd4a4ec4cdba4f35c00e002a136f00e96a908b0a":
        raise ValueError("CI font archive checksum mismatch")
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        for entry in archive.infolist():
            if entry.filename.endswith(".ttf"):
                (fonts / Path(entry.filename).name).write_bytes(archive.read(entry))
    os.environ["FONTCONFIG_FILE"] = str(config)
    os.environ["FONTCONFIG_PATH"] = str(root)
    with Path(os.environ["GITHUB_ENV"]).open("a", encoding="utf-8") as environment:
        environment.write("FONTCONFIG_FILE=" + str(config) + "\nFONTCONFIG_PATH=" + str(root) + "\n")
    probe = root / "CiFontProbe.java"
    probe.write_text('class CiFontProbe { public static void main(String[] args) { System.out.println("CI fonts available: " + java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames().length); } }', encoding="utf-8")
    subprocess.run([str(Path(os.environ["JAVA_HOME"]) / "bin/java"), "-Djava.awt.headless=true", str(probe)], check=True)
