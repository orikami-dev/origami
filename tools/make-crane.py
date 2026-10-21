#!/usr/bin/env python3
"""Régénère les icônes du lanceur (res/drawable/ic_foreground*.xml) depuis la grue en vol de Crane.FLY.
Les nuances de chaque thème sont relues dans l'ancien fichier (chemins 0, 1, 4, 6 et 10 = nuances 1, 3, 0, 2, 4)
ou, au deuxième passage, dans le commentaire « nuances » du fichier déjà généré."""
import glob, re, os
FLY = [
 (4, [14,31,30,21,31,35]), (2, [30,21,34,22,31,35]), (3, [31,35,34,22,48,46,42,60,30,59]),
 (1, [66,6,42,36,49,47]), (2, [66,6,49,47,65,36]), (4, [98,11,50,49,42,60]), (2, [98,11,42,60,66,65]),
 (1, [38,60,66,66,48,93]), (0, [30,59,38,60,48,93,38,90]), (2, [66,66,94,85,59,78]),
]
BEAK = [14,31,30,21,31,35]
here = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'res', 'drawable')

def shades(txt):
    m = re.search(r'nuances: (#\w+(?: #\w+){4})', txt)
    if m:
        return m.group(1).split()
    cols = re.findall(r'fillColor="(#\w+)"', txt)
    return [cols[4], cols[0], cols[6], cols[1], cols[10]]

def dark(c):
    r, g, b = int(c[3:5], 16), int(c[5:7], 16), int(c[7:9], 16)
    return '#FF%02X%02X%02X' % (int(r * .55), int(g * .55), int(b * .55))

def d(pts):
    return 'M' + 'L'.join('%g,%g' % (pts[i], pts[i+1]) for i in range(0, len(pts), 2)) + 'Z'

for path in sorted(glob.glob(os.path.join(here, 'ic_foreground*.xml'))):
    sh = shades(open(path, encoding='utf-8').read())
    gold = '#FFFFD23F'
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- Grue en vol (0.66.12), même dessin que Crane.FLY ; généré par tools/make-crane.py.',
           '     nuances: %s -->' % ' '.join(sh),
           '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">',
           '    <group android:pivotX="56" android:pivotY="49.5" android:scaleX="0.66" android:scaleY="0.66" android:translateX="-2" android:translateY="4.5">',
           '        <group android:translateX="1.8" android:translateY="3.2">']
    for k, p in FLY:
        out.append('            <path android:fillColor="#38000000" android:strokeColor="#38000000" android:strokeWidth="1.4" android:strokeLineJoin="round" android:pathData="%s" />' % d(p))
    out.append('        </group>')
    for i, (k, p) in enumerate(FLY):
        fill = gold if i == 0 else sh[k]
        out.append('        <path android:fillColor="%s" android:strokeColor="%s" android:strokeWidth="1.4" android:strokeLineJoin="round" android:pathData="%s" />' % (fill, dark(fill), d(p)))
    out += ['    </group>', '</vector>', '']
    open(path, 'w', encoding='utf-8').write('\n'.join(out))
    print(os.path.basename(path), sh)
