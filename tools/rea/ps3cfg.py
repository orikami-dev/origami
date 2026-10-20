import sys, re, struct, json, subprocess
sys.path.insert(0, __import__('os').path.dirname(__file__))
from loader import Lib

lib = Lib(sys.argv[1])
uc = lib.uc

# 1) Valeurs d'enum : fmt_class_string<E>::format(out, i)
enums = {}
out = {'s': None}


def h_append(l):
    p, n = l.reg(1), l.reg(2)
    out['s'] = (out['s'] or '') + bytes(l.uc.mem_read(p, n)).decode('latin-1')


def h_append_c(l):
    out['s'] = (out['s'] or '') + l.cstr(l.reg(1))


def h_int(l):
    out['s'] = None
    out['bad'] = True


lib.handlers['_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendEPKcm'] = h_append
lib.handlers['_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendEPKc'] = h_append_c
lib.handlers['_ZN16fmt_class_stringIiE6formatERNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEEm'] = h_int

pat = re.compile(r'^_ZN16fmt_class_stringI(\d+)([A-Za-z_0-9]+)E6formatERNSt6__ndk112basic_stringIcNS2_11char_traitsIcEENS2_9allocatorIcEEEEm$')
for name, addr in lib.byname.items():
    m = pat.match(name)
    if not m:
        continue
    n = int(m.group(1))
    en = m.group(2)[:n]
    if m.group(2)[n:]:
        continue
    vals = []
    buf = lib.alloc(64)
    for i in range(0, 40):
        out['s'] = None
        out['bad'] = False
        try:
            lib.call(addr, [buf, i], count=200000)
        except Exception as e:
            vals.append('<err>')
            break
        if out['bad']:
            break
        vals.append(out['s'])
    enums[en] = vals
lib.handlers.clear()

# 2) Arbre cfg_root
nodes = []


def h_base(l):
    this, typ, owner, name, dyn = l.reg(0), l.reg(1), l.reg(2), l.stdstr(l.reg(3)), l.reg(4) & 0xff
    nodes.append(dict(this=this, type=typ & 0xff, owner=owner, name=name, dynamic=dyn))
    l.setreg(0, this)


lib.handlers['_ZN3cfg5_baseC2ENS_4typeEPNS_4nodeENSt6__ndk112basic_stringIcNS4_11char_traitsIcEENS4_9allocatorIcEEEEb'] = h_base
lib.handlers['_ZN3cfg5_baseC1ENS_4typeEPNS_4nodeENSt6__ndk112basic_stringIcNS4_11char_traitsIcEENS4_9allocatorIcEEEEb'] = h_base
def h_string(l):
    this, owner, name, defv = l.reg(0), l.reg(1), l.stdstr(l.reg(2)), l.stdstr(l.reg(3))
    nodes.append(dict(this=this, type=-1, owner=owner, name=name, dynamic=l.reg(4) & 0xff, cls='cfg::string', default=defv))
    l.uc.mem_write(this, b'\0' * 8)
    l.setreg(0, this)


for nm in list(lib.byname):
    if nm.startswith('_ZN3cfg6stringC2EPNS_4node') or nm.startswith('_ZN3cfg6stringC1EPNS_4node'):
        lib.handlers[nm] = h_string
        lib.hook_addr(lib.byname[nm], h_string)
    if nm.startswith('_ZN3cfg5_baseC2ENS_4typeEPNS_4node') or nm.startswith('_ZN3cfg5_baseC1ENS_4typeEPNS_4node'):
        lib.hook_addr(lib.byname[nm], h_base)
lib.follow = True
lib.nofollow_sub = ['fmt', 'log', 'basic_string', 'to_string']
root = lib.alloc(0x200000)
lib.call(lib.byname['_ZN8cfg_rootC2Ev'], [root])
for c in ['_ZN8cfg_root8node_netC2EPN3cfg4nodeE', '_ZN8cfg_root14node_savestateC2EPN3cfg4nodeE', '_ZN8cfg_root9node_miscC2EPN3cfg4nodeE']:
    if not any(n['name'] in ('Net', 'Savestate', 'Miscellaneous') and n['owner'] == root for n in nodes) or True:
        buf = lib.alloc(0x20000)
        try:
            lib.call(lib.byname[c], [buf, root])
        except Exception as e:
            print('ERR', c, e)


def tname(vt):
    try:
        ti = lib.u64(vt - 8)
        nm = lib.u64(ti + 8)
        return lib.cstr(nm)
    except Exception:
        return '?'


by_this = {n['this']: n for n in nodes}
by_this[root] = dict(name='', owner=0)


def path(n):
    parts = []
    while n and n.get('name'):
        parts.append(n['name'])
        n = by_this.get(n['owner'])
    return '@@'.join(reversed(parts))


res = []
for n in nodes:
    vt = lib.u64(n['this'])
    tn = n.get('cls') or tname(vt)
    # valeur par défaut : premier champ après la base (cfg::_base fait 0x58 octets ici)
    raw = bytes(uc.mem_read(n['this'] + 0x58, 16))
    res.append(dict(path=path(n), type=n['type'], cls=tn, dynamic=n['dynamic'], raw=raw.hex()))
json.dump(dict(enums=enums, nodes=res, calls=lib.calls), open(sys.argv[2], 'w'), indent=1)
print(len(enums), 'enums,', len(res), 'nodes')
