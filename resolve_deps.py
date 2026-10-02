#!/usr/bin/env python3
"""Manual Maven dependency resolver for the TryFit APK build (v2: parallel)."""
import os, re, sys, zipfile, threading
import xml.etree.ElementTree as ET
import requests
from concurrent.futures import ThreadPoolExecutor, as_completed

REPOS = [
    'https://dl.google.com/dl/android/maven2',
    'https://repo1.maven.org/maven2',
]
OUT = '/home/hatch/workspace/virtual-tryon-app/.manual-libs'
os.makedirs(OUT + '/jars', exist_ok=True)
os.makedirs(OUT + '/aar-classes', exist_ok=True)

NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
T = (8, 25)  # connect, read timeouts

_local = threading.local()
def S():
    if not hasattr(_local, 's'):
        s = requests.Session()
        s.headers['User-Agent'] = 'tryfit-manual-build/2.0'
        _local.s = s
    return _local.s

def repo_file(repo, g, a, v, filename):
    return f"{repo}/{g.replace('.', '/')}/{a}/{v}/{filename}"

def find_url(g, a, v, filename):
    for repo in REPOS:
        u = repo_file(repo, g, a, v, filename)
        try:
            r = S().head(u, timeout=T, allow_redirects=True)
            if r.status_code == 200:
                return u
        except Exception:
            continue
    return None

def get_text(url):
    r = S().get(url, timeout=T)
    r.raise_for_status()
    return r.text

def get_bytes(url):
    r = S().get(url, timeout=(10, 300))
    r.raise_for_status()
    return r.content

def metadata_url(g, a):
    for repo in REPOS:
        u = f"{repo}/{g.replace('.', '/')}/{a}/maven-metadata.xml"
        try:
            r = S().head(u, timeout=T, allow_redirects=True)
            if r.status_code == 200:
                return u
        except Exception:
            continue
    return None

POM_CACHE = {}
POM_LOCK = threading.Lock()

def fetch_pom(g, a, v):
    key = (g, a, v)
    with POM_LOCK:
        if key in POM_CACHE:
            return POM_CACHE[key]
    u = find_url(g, a, v, f"{a}-{v}.pom")
    pom = None
    if u:
        try:
            pom = ET.fromstring(get_text(u))
        except Exception:
            pom = None
    with POM_LOCK:
        POM_CACHE[key] = pom
    return pom

def latest_version(g, a):
    u = metadata_url(g, a)
    if not u:
        return None
    try:
        root = ET.fromstring(get_text(u))
        vers = [x.text for x in root.findall('.//m:version', NS)]
        rel = root.find('.//m:release', NS)
        return rel.text if rel is not None and rel.text else (vers[-1] if vers else None)
    except Exception:
        return None

def merge_props(pom, props):
    parent = pom.find('m:parent', NS)
    if parent is not None:
        gp = parent.findtext('m:groupId', default='', namespaces=NS)
        ap = parent.findtext('m:artifactId', default='', namespaces=NS)
        vp = parent.findtext('m:version', default='', namespaces=NS)
        pp = fetch_pom(gp, ap, vp)
        if pp is not None:
            merge_props(pp, props)
    for p in pom.findall('m:properties/*', NS):
        tag = p.tag.split('}')[1]
        props.setdefault(tag, (p.text or '').strip())

def subst(text, props, g, a, v):
    if not text:
        return text
    def rep(m):
        k = m.group(1)
        if k in ('project.version', 'pom.version'):
            return v
        if k == 'project.groupId':
            return g
        if k == 'project.artifactId':
            return a
        return props.get(k, m.group(0))
    for _ in range(10):
        new = re.sub(r'\$\{([^}]+)\}', rep, text)
        if new == text:
            break
        text = new
    return text

def managed_versions(pom, g, a, v, acc):
    parent = pom.find('m:parent', NS)
    if parent is not None:
        gp = parent.findtext('m:groupId', default='', namespaces=NS)
        ap = parent.findtext('m:artifactId', default='', namespaces=NS)
        vp = parent.findtext('m:version', default='', namespaces=NS)
        pp = fetch_pom(gp, ap, vp)
        if pp is not None:
            managed_versions(pp, gp, ap, vp, acc)
    props = {}
    merge_props(pom, props)
    dm = pom.find('m:dependencyManagement/m:dependencies', NS)
    if dm is not None:
        for d in dm.findall('m:dependency', NS):
            dg = subst(d.findtext('m:groupId', default='', namespaces=NS), props, g, a, v)
            da = subst(d.findtext('m:artifactId', default='', namespaces=NS), props, g, a, v)
            dv = subst(d.findtext('m:version', default='', namespaces=NS), props, g, a, v)
            dt = d.findtext('m:type', default='jar', namespaces=NS)
            ds = d.findtext('m:scope', default='', namespaces=NS)
            if dt == 'pom' and ds == 'import':
                if dv and '${' not in dv:
                    bp = fetch_pom(dg, da, dv)
                    if bp is not None:
                        managed_versions(bp, dg, da, dv, acc)
                continue
            if dv and '${' not in dv:
                acc.setdefault((dg, da), dv)

def pom_deps(pom, g, a, v):
    props = {}
    merge_props(pom, props)
    out = []
    deps = pom.find('m:dependencies', NS)
    if deps is None:
        return out
    for d in deps.findall('m:dependency', NS):
        dg = subst(d.findtext('m:groupId', default='', namespaces=NS), props, g, a, v)
        da = subst(d.findtext('m:artifactId', default='', namespaces=NS), props, g, a, v)
        dv = subst(d.findtext('m:version', default='', namespaces=NS), props, g, a, v)
        dt = d.findtext('m:type', default='jar', namespaces=NS)
        scope = d.findtext('m:scope', default='compile', namespaces=NS)
        opt = d.findtext('m:optional', default='false', namespaces=NS) == 'true'
        if opt or scope not in ('compile', 'runtime') or dt not in ('jar', 'aar', 'bundle'):
            continue
        if not dv or '${' in dv:
            continue
        out.append((dg, da, dv))
    return out

KMP_GROUPS = {'io.github.jan-tennert.supabase', 'io.ktor', 'io.coil-kt.coil3',
              'org.jetbrains.kotlinx'}

def best_pom(g, a, v):
    if g in KMP_GROUPS:
        for suffix in ('-android', '-jvm'):
            p = fetch_pom(g, a + suffix, v)
            if p is not None:
                return p, a + suffix
    p = fetch_pom(g, a, v)
    if p is not None:
        if not pom_deps(p, g, a, v):
            for suffix in ('-android', '-jvm'):
                pv = fetch_pom(g, a + suffix, v)
                if pv is not None and pom_deps(pv, g, a + suffix, v):
                    return pv, a + suffix
        return p, a
    for suffix in ('-android', '-jvm'):
        pv = fetch_pom(g, a + suffix, v)
        if pv is not None:
            return pv, a + suffix
    return None, a

def pick_variant(g, a, v):
    for aid, ext in ((f"{a}-android", 'aar'), (f"{a}-jvm", 'jar'), (a, 'aar'), (a, 'jar')):
        u = find_url(g, aid, v, f"{aid}-{v}.{ext}")
        if u:
            return aid, ext, u
    return None, None, None

def main():
    direct = [
        ('androidx.core', 'core-ktx', '1.16.0'),
        ('androidx.core', 'core-splashscreen', '1.2.0'),
        ('androidx.activity', 'activity-compose', '1.11.0'),
        ('androidx.lifecycle', 'lifecycle-runtime-compose', '2.11.0'),
        ('androidx.lifecycle', 'lifecycle-viewmodel-compose', '2.11.0'),
        ('androidx.compose.ui', 'ui', None),
        ('androidx.compose.ui', 'ui-graphics', None),
        ('androidx.compose.ui', 'ui-tooling-preview', None),
        ('androidx.compose.material3', 'material3', None),
        ('androidx.compose.material', 'material-icons-core', None),
        ('androidx.navigation', 'navigation-compose', '2.10.2'),
        ('io.coil-kt.coil3', 'coil-compose', '3.6.3'),
        ('io.coil-kt.coil3', 'coil-network-okhttp', '3.6.3'),
        ('androidx.camera', 'camera-core', '1.6.2'),
        ('androidx.camera', 'camera-camera2', '1.6.2'),
        ('androidx.camera', 'camera-lifecycle', '1.6.2'),
        ('androidx.camera', 'camera-view', '1.6.2'),
        ('io.github.jan-tennert.supabase', 'supabase-kt', None),
        ('io.github.jan-tennert.supabase', 'postgrest-kt', None),
        ('io.github.jan-tennert.supabase', 'storage-kt', None),
        ('io.github.jan-tennert.supabase', 'realtime-kt', None),
        ('io.github.jan-tennert.supabase', 'auth-kt', None),
        ('io.github.jan-tennert.supabase', 'functions-kt', None),
        ('io.ktor', 'ktor-client-okhttp', '3.1.2'),
        ('org.jetbrains.kotlinx', 'kotlinx-serialization-json', '1.9.0'),
        ('androidx.datastore', 'datastore-preferences', '1.2.0'),
        ('androidx.room', 'room-runtime', '2.8.5'),
        ('androidx.room', 'room-ktx', '2.8.5'),
        ('androidx.work', 'work-runtime-ktx', '2.10.2'),
        ('org.jetbrains.kotlinx', 'kotlinx-coroutines-android', '1.10.2'),
        ('androidx.profileinstaller', 'profileinstaller', '1.4.1'),
        ('org.jetbrains.kotlin', 'kotlin-stdlib', '2.1.21'),
        ('androidx.compose.runtime', 'runtime', None),
        ('androidx.compose.foundation', 'foundation', None),
    ]
    print('fetching BOMs...', flush=True)
    managed = {}
    with ThreadPoolExecutor(max_workers=4) as ex:
        futs = {ex.submit(fetch_pom, bg, ba, bv): (bg, ba, bv)
                for bg, ba, bv in [('androidx.compose', 'compose-bom', '2026.09.00'),
                                   ('io.github.jan-tennert.supabase', 'bom', '3.1.4')]}
        for f in as_completed(futs):
            bg, ba, bv = futs[f]
            bp = f.result()
            if bp is None:
                print(f'WARN: BOM {bg}:{ba}:{bv} missing', flush=True)
            else:
                managed_versions(bp, bg, ba, bv, managed)
    print(f'managed versions: {len(managed)}', flush=True)

    resolved = {}
    wave = []
    for g, a, v in direct:
        if v is None:
            v = managed.get((g, a)) or latest_version(g, a)
        if v is None:
            print(f'WARN: no version for {g}:{a}', flush=True)
            continue
        wave.append((g, a, v))

    with ThreadPoolExecutor(max_workers=12) as ex:
        while wave:
            print(f'wave: {len(wave)} artifacts, resolved so far: {len(resolved)}', flush=True)
            futs = {ex.submit(best_pom, g, a, v): (g, a, v) for g, a, v in wave}
            wave = []
            for f in as_completed(futs):
                g, a, v = futs[f]
                if (g, a) in resolved:
                    continue
                try:
                    pom, ea = f.result()
                except Exception as e:
                    print(f'WARN pom fail {g}:{a}:{v}: {e}', flush=True)
                    continue
                resolved[(g, a)] = v
                if pom is None:
                    print(f'WARN: no pom {g}:{a}:{v}', flush=True)
                    continue
                for dg, da, dv in pom_deps(pom, g, ea, v):
                    if (dg, da) in resolved:
                        continue
                    dv = dv or managed.get((dg, da))
                    if not dv:
                        continue
                    wave.append((dg, da, dv))
            # de-dupe wave preserving order
            seenw = set()
            wave = [x for x in wave if not (x in seenw or seenw.add(x))]
    print(f'resolved {len(resolved)} modules', flush=True)

    jobs = []
    def pv_job(gav):
        g, a, v = gav
        aid, ext, url = pick_variant(g, a, v)
        if aid is None:
            print(f'WARN: no binary for {g}:{a}:{v}', flush=True)
            return None
        return (g, aid, v, ext, url)
    with ThreadPoolExecutor(max_workers=12) as ex:
        for r in ex.map(pv_job, list(resolved.items()) and [(g, a, v) for (g, a), v in resolved.items()]):
            if r:
                jobs.append(r)
    print(f'binaries: {len(jobs)}', flush=True)

    def dl(job):
        g, aid, v, ext, url = job
        dest = f"{OUT}/jars/{g}_{aid}-{v}.{ext}"
        if os.path.exists(dest) and os.path.getsize(dest) > 0:
            return 'cached'
        try:
            open(dest, 'wb').write(get_bytes(url))
            return 'ok'
        except Exception as e:
            return f'FAIL {e}'

    fails = 0
    with ThreadPoolExecutor(max_workers=8) as ex:
        futs = {ex.submit(dl, j): j for j in jobs}
        for i, f in enumerate(as_completed(futs)):
            st = f.result()
            if st not in ('ok', 'cached'):
                fails += 1
                print(f'DL {futs[f][:3]}: {st}', flush=True)
            if (i + 1) % 40 == 0:
                print(f'downloaded {i+1}/{len(jobs)}', flush=True)
    print(f'downloads done, fails={fails}', flush=True)

    n = 0
    for fn in os.listdir(OUT + '/jars'):
        if not fn.endswith('.aar'):
            continue
        out = OUT + '/aar-classes/' + fn[:-4] + '-classes.jar'
        if os.path.exists(out):
            continue
        try:
            z = zipfile.ZipFile(OUT + '/jars/' + fn)
            for name in z.namelist():
                if name == 'classes.jar':
                    open(out, 'wb').write(z.read(name))
                    n += 1
                    break
        except Exception as e:
            print(f'AAR extract fail {fn}: {e}', flush=True)
    print(f'extracted {n} classes.jar', flush=True)

    cps = []
    for fn in sorted(os.listdir(OUT + '/jars')):
        if fn.endswith('.jar'):
            cps.append(OUT + '/jars/' + fn)
    for fn in sorted(os.listdir(OUT + '/aar-classes')):
        if fn.endswith('.jar'):
            cps.append(OUT + '/aar-classes/' + fn)
    open(OUT + '/classpath.txt', 'w').write(':'.join(cps))
    open(OUT + '/resolved.txt', 'w').write('\n'.join(f'{g}:{a}:{v}' for (g, a), v in sorted(resolved.items())))
    print(f'classpath entries: {len(cps)}', flush=True)

if __name__ == '__main__':
    main()
