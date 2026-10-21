"""Charge un .so aarch64 dans Unicorn, applique les relocations, intercepte les appels PLT."""
import struct, sys
from elftools.elf.elffile import ELFFile
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE, UC_HOOK_MEM_UNMAPPED, UcError
from unicorn.arm64_const import *

STUB = 0x70000000
HEAP = 0x80000000
STACK = 0x60000000
ZERO = 0x71000000
END = 0x7f000000

R = {i: getattr(__import__('unicorn.arm64_const', fromlist=['x']), 'UC_ARM64_REG_X%d' % i) for i in range(31)}


def raise_stop(l, name):
    print('STOP:', name, 'lr=%x' % l.reg(30))
    l.uc.emu_stop()


class Lib:
    def __init__(self, path):
        self.f = open(path, 'rb')
        self.elf = ELFFile(self.f)
        self.data = open(path, 'rb').read()
        self.uc = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
        top = 0
        for seg in self.elf.iter_segments():
            if seg['p_type'] == 'PT_LOAD':
                top = max(top, seg['p_vaddr'] + seg['p_memsz'])
        top = (top + 0xfff) & ~0xfff
        self.uc.mem_map(0, top)
        for seg in self.elf.iter_segments():
            if seg['p_type'] == 'PT_LOAD':
                self.uc.mem_write(seg['p_vaddr'], self.data[seg['p_offset']:seg['p_offset'] + seg['p_filesz']])
        self.uc.mem_map(STACK, 0x1000000)
        self.uc.mem_map(HEAP, 0x10000000)
        self.uc.mem_map(ZERO, 0x100000)
        self.heap = HEAP + 0x1000
        self.dynsym = self.elf.get_section_by_name('.dynsym')
        self.syms = {}
        self.byname = {}
        for s in self.dynsym.iter_symbols():
            if s['st_shndx'] != 'SHN_UNDEF' and s['st_value']:
                self.syms.setdefault(s['st_value'], s.name)
                self.byname[s.name] = s['st_value']
        self.stubnames = []
        self._relocate()
        self.uc.mem_map(STUB, ((len(self.stubnames) * 4 + 0xfff) & ~0xfff) + 0x1000)
        self.uc.mem_write(STUB, b'\xc0\x03\x5f\xd6' * (len(self.stubnames) + 1))
        self.uc.mem_write(END, b'') if False else None
        self.uc.mem_map(END, 0x1000)
        self.uc.mem_write(END, b'\xc0\x03\x5f\xd6')
        self.uc.hook_add(UC_HOOK_CODE, self._stub_hook, begin=STUB, end=STUB + len(self.stubnames) * 4)
        self.handlers = {}
        self.calls = {}
        self.follow = False
        self.nofollow = set()
        self.nofollow_sub = []

    def _relocate(self):
        uc = self.uc
        symtab = list(self.dynsym.iter_symbols())
        for secname in ('.rela.dyn', '.rela.plt'):
            sec = self.elf.get_section_by_name(secname)
            for r in sec.iter_relocations():
                t = r['r_info_type']
                off = r['r_offset']
                add = r['r_addend']
                if t == 1027:  # RELATIVE
                    uc.mem_write(off, struct.pack('<Q', add & 0xffffffffffffffff))
                elif t in (1025, 257, 1026):  # GLOB_DAT, ABS64, JUMP_SLOT
                    s = symtab[r['r_info_sym']]
                    if t == 1026 or (s['st_info']['type'] == 'STT_FUNC'):
                        i = len(self.stubnames)
                        self.stubnames.append(s.name)
                        uc.mem_write(off, struct.pack('<Q', STUB + 4 * i))
                    elif s['st_shndx'] != 'SHN_UNDEF':
                        uc.mem_write(off, struct.pack('<Q', (s['st_value'] + add) & 0xffffffffffffffff))
                    else:
                        uc.mem_write(off, struct.pack('<Q', ZERO))
        # RELR
        sec = self.elf.get_section_by_name('.relr.dyn')
        if sec is not None:
            d = sec.data()
            where = 0
            for k in range(0, len(d), 8):
                e = struct.unpack_from('<Q', d, k)[0]
                if e & 1 == 0:
                    v = struct.unpack('<Q', uc.mem_read(e, 8))[0]
                    uc.mem_write(e, struct.pack('<Q', v))  # base 0: no change
                    where = e + 8
                else:
                    where += 63 * 8

    def reg(self, i):
        return self.uc.reg_read(R[i])

    def setreg(self, i, v):
        self.uc.reg_write(R[i], v)

    def alloc(self, n):
        p = self.heap
        self.heap += (n + 15) & ~15
        return p

    def cstr(self, p, n=4096):
        b = bytes(self.uc.mem_read(p, n))
        return b.split(b'\0')[0].decode('latin-1')

    def u64(self, p):
        return struct.unpack('<Q', self.uc.mem_read(p, 8))[0]

    def stdstr(self, p):
        """libc++ std::string (disposition standard, petit-boutiste)."""
        b0 = self.uc.mem_read(p, 1)[0]
        if b0 & 1 == 0:
            n = b0 >> 1
            return bytes(self.uc.mem_read(p + 1, n)).decode('latin-1')
        n = self.u64(p + 8)
        ptr = self.u64(p + 16)
        return bytes(self.uc.mem_read(ptr, n)).decode('latin-1')

    def _stub_hook(self, uc, addr, size, ud):
        i = (addr - STUB) // 4
        if i >= len(self.stubnames):
            return
        name = self.stubnames[i]
        self.calls[name] = self.calls.get(name, 0) + 1
        h = self.handlers.get(name)
        if h:
            h(self)
            return
        if name == '__cxa_throw' or name == 'abort':
            raise_stop(self, name)
            return
        if self.follow and name in self.byname and name not in self.nofollow and not any(k in name for k in self.nofollow_sub):
            uc.reg_write(UC_ARM64_REG_PC, self.byname[name])
            return
        if name in ('_Znwm', '_Znam', 'malloc'):
            self.setreg(0, self.alloc(self.reg(0)))
        elif name == 'calloc':
            self.setreg(0, self.alloc(self.reg(0) * self.reg(1)))
        elif name in ('memcpy', 'memmove'):
            d, s, n = self.reg(0), self.reg(1), self.reg(2)
            if n:
                uc.mem_write(d, bytes(uc.mem_read(s, n)))
        elif name == 'memset':
            d, c, n = self.reg(0), self.reg(1), self.reg(2)
            if n:
                uc.mem_write(d, bytes([c & 0xff]) * n)
        elif name == 'strlen':
            self.setreg(0, len(self.cstr(self.reg(0)).encode('latin-1')))
        else:
            self.setreg(0, 0)

    def hook_addr(self, addr, fn):
        def h(uc, a, size, ud):
            fn(self)
            uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_X30))
        self.uc.hook_add(UC_HOOK_CODE, h, begin=addr, end=addr)

    def call(self, addr, args, count=50_000_000):
        sp = STACK + 0xff0000
        self.uc.reg_write(UC_ARM64_REG_SP, sp)
        for k, a in enumerate(args):
            self.setreg(k, a)
        self.setreg(30, END)
        try:
            self.uc.emu_start(addr, END, count=count)
        except UcError as e:
            pc = self.uc.reg_read(UC_ARM64_REG_PC)
            raise RuntimeError('emu error %s at pc=%x' % (e, pc))
        return self.reg(0)
