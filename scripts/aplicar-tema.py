#!/usr/bin/env python3
"""
aplicar-tema.py — Porto Ajuda
Centraliza as cores do site em variáveis (public/styles/tema.css) e
troca o tema: o roxo que era o ESCURO passa a ser o CLARO e o verde sai.

Uso (na raiz do repositório):
    python scripts/aplicar-tema.py            # aplica
    python scripts/aplicar-tema.py --dry-run  # só mostra o que mudaria

Pré-requisito: public/styles/tema.css já existir.
O script é idempotente: rodar duas vezes não muda nada na segunda.
"""
import re, sys, glob, os, collections

try:
    sys.stdout.reconfigure(encoding='utf-8')   # console do Windows
except Exception:
    pass

DRY = '--dry-run' in sys.argv
ROOT = os.path.join('public', 'styles')
if not os.path.isfile(os.path.join(ROOT, 'tema.css')):
    sys.exit('ERRO: public/styles/tema.css não existe. Crie-o antes.')

_FMT = {}   # caminho -> (usa_crlf, tem_bom)

def read(p):
    with open(p, 'rb') as f:
        raw = f.read()
    bom = raw.startswith(b'\xef\xbb\xbf')
    txt = raw.decode('utf-8-sig')
    crlf = '\r\n' in txt
    _FMT[p] = (crlf, bom)
    return txt.replace('\r\n', '\n')

def write(p, t):
    if DRY:
        return
    crlf, bom = _FMT.get(p, (False, False))
    if crlf:
        t = t.replace('\n', '\r\n')
    with open(p, 'wb') as f:
        f.write((b'\xef\xbb\xbf' if bom else b'') + t.encode('utf-8'))

# ---------------------------------------------------------------- parser
def parse(text):
    """Regras folha (com declarações): dicts start,end,sel,bs,be."""
    out, stack, i, n, sel_start = [], [], 0, len(text), 0
    while i < n:
        c = text[i]
        if text.startswith('/*', i):
            j = text.find('*/', i + 2)
            i = n if j < 0 else j + 2
            continue
        if c in '"\'':
            q = c; i += 1
            while i < n and text[i] != q:
                if text[i] == '\\': i += 1
                i += 1
            i += 1
            continue
        if c == '(':
            d = 1; i += 1
            while i < n and d:
                if text[i] == '(': d += 1
                elif text[i] == ')': d -= 1
                i += 1
            continue
        if c == '{':
            stack.append((sel_start, i, text[sel_start:i])); sel_start = i + 1
        elif c == '}':
            if stack:
                s, o, sel = stack.pop()
                if '{' not in text[o + 1:i]:
                    out.append(dict(start=s, end=i + 1, bs=o + 1, be=i,
                                    sel=re.sub(r'/\*.*?\*/', '', sel, flags=re.S).strip()))
            sel_start = i + 1
        elif c == ';' and not stack:
            sel_start = i + 1
        i += 1
    return out

def split_selectors(sel):
    parts, d, cur = [], 0, ''
    for ch in sel:
        if ch in '([': d += 1
        if ch in ')]': d -= 1
        if ch == ',' and d == 0:
            parts.append(cur); cur = ''
        else:
            cur += ch
    parts.append(cur)
    return [p.strip() for p in parts if p.strip()]

# ----------------------------------------------------- mapa de cores
def norm_hex(h):
    h = h.lower()
    if len(h) == 4: h = '#' + ''.join(c * 2 for c in h[1:])
    return h

TEXT_PROPS = re.compile(r'^(color|caret-color|accent-color|fill|stroke|text-decoration-color|-webkit-text-fill-color|-webkit-text-stroke(-color)?|outline(-color)?|border(-(top|right|bottom|left|inline|block))*(-color)?|column-rule(-color)?)$')

MAP_ANY = {      # vale para qualquer propriedade
    '#161928': 'var(--bg-main)', '#2e1c50': 'var(--bg-section)',
    '#3e2871': 'var(--bg-card)', '#3d2775': 'var(--bg-card)',
    '#453b80': 'var(--bg-card-hover)',
    '#4a5492': 'var(--primary)', '#5b68ac': 'var(--secondary)',
    '#f2aa1a': 'var(--gold)', '#f5b52e': 'var(--gold)',
    '#ddaa41': 'var(--gold-dark)',
    '#f9c86b': 'var(--gold-light)', '#ffd16a': 'var(--gold-light)', '#f0ce76': 'var(--gold-light)',
    '#fce0a1': 'var(--gold-soft)',
    '#d7d7d7': 'var(--text-soft)', '#e6e1ee': 'var(--text-soft)',
    '#f8f4ff': 'var(--text)', '#f8f5ff': 'var(--text)',
    '#211a38': 'var(--bg-deep)', '#211a3e': 'var(--bg-deep)', '#211a31': 'var(--bg-deep)',
}
ON_GOLD_ONLY_COLOR = {   # roxos escuros usados como COR DE TEXTO (sobre dourado)
    '#161928', '#211a38', '#211a3e', '#211a31', '#20172c', '#261a34', '#25183f', '#2e1c50',
}
RGBA_MAP = {
    (255, 255, 255): 'var(--ink-rgb)',
    (242, 170, 26): 'var(--gold-rgb)', (245, 181, 46): 'var(--gold-rgb)', (249, 200, 107): 'var(--gold-rgb)',
    (22, 25, 40): 'var(--bg-main-rgb)', (46, 28, 80): 'var(--bg-section-rgb)', (62, 40, 113): 'var(--bg-card-rgb)',
}

def mask(value):
    """Esconde url(...) e var(...) (para não mexer em fallbacks/data-URIs)."""
    store = []
    def keep(m):
        store.append(m.group(0)); return f'\x00{len(store)-1}\x00'
    out, i = '', 0
    while i < len(value):
        m = re.match(r'(url|var)\(', value[i:], re.I)
        if m:
            d, j = 1, i + m.end()
            while j < len(value) and d:
                if value[j] == '(': d += 1
                elif value[j] == ')': d -= 1
                j += 1
            store.append(value[i:j]); out += f'\x00{len(store)-1}\x00'; i = j
        else:
            out += value[i]; i += 1
    return out, store

def unmask(v, store):
    return re.sub(r'\x00(\d+)\x00', lambda m: store[int(m.group(1))], v)

stats = collections.Counter()

def convert_value(prop, value):
    p = prop.lower()
    if p.startswith('--'):
        return value
    v, store = mask(value)
    is_text = bool(TEXT_PROPS.match(p))
    is_color_prop = p == 'color'

    def hex_sub(m):
        h = norm_hex(m.group(0))
        if h == '#ffffff':
            if is_text:
                stats['#fff → --text'] += 1; return 'var(--text)'
            return m.group(0)                      # fundo branco: não mexe
        if is_color_prop and h in ON_GOLD_ONLY_COLOR:
            stats['texto escuro → --on-gold'] += 1; return 'var(--on-gold)'
        if h in MAP_ANY:
            stats[f'{h} → {MAP_ANY[h]}'] += 1; return MAP_ANY[h]
        return m.group(0)
    v = re.sub(r'#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{3})\b', hex_sub, v)

    def rgba_sub(m):
        r, g, b = int(m.group(2)), int(m.group(3)), int(m.group(4))
        if (r, g, b) in RGBA_MAP and m.group(5) is not None:
            stats[f'rgba({r},{g},{b},α) → {RGBA_MAP[(r,g,b)]}'] += 1
            return f'rgba({RGBA_MAP[(r,g,b)]}, {m.group(5).strip()})'
        return m.group(0)
    v = re.sub(r'(rgba?)\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([\d.]+%?)\s*\)', rgba_sub, v)
    return unmask(v, store)

def convert_body(body):
    def decl(m):
        prop, val = m.group(1), m.group(2)
        return f'{prop}{m.group(3)}{convert_value(prop, val)}'
    return re.sub(r'((?:^|[;{\s])(?:--)?[a-zA-Z-]+)(\s*:\s*)([^;{}]*)',
                  lambda m: m.group(1) + m.group(2) + convert_value(m.group(1).strip(' ;{\n\t\r'), m.group(3)), body)

def convert_css(text, skip_sel):
    rules = parse(text)
    out, last = [], 0
    for r in rules:
        sel = r['sel']
        if sel.startswith('@') or skip_sel(sel):
            continue
        out.append(text[last:r['bs']]); out.append(convert_body(text[r['bs']:r['be']])); last = r['be']
    out.append(text[last:])
    return ''.join(out)

# --------------------------------------------- :root duplicados nas páginas
CENTRAL = set('''bg-main bg-section bg-card bg-card-hover primary primary-hover secondary secondary-hover
gold gold-dark gold-light gold-soft text text-soft text-muted text-disabled border border-light border-gold
dark-contrast purple purple-dark purple-light success warning error info'''.split())
RENAME = {'primary': 'ui-primary', 'primary-hover': 'ui-primary-hover',
          'secondary': 'ui-secondary', 'secondary-hover': 'ui-secondary-hover'}
RENAME_FILES = {'perfil.css', 'cadastro-osc.css', 'login-osc.css'}

def strip_root(text, fname):
    removed = []
    def blk(m):
        body = m.group(1)
        def d(mm):
            name = mm.group(1)
            if name in CENTRAL:
                removed.append(name); return ''
            return mm.group(0)
        body2 = re.sub(r'[ \t]*--([a-z0-9-]+)\s*:[^;]+;[ \t]*\n?', d, body)
        if not re.search(r'--[a-z0-9-]+\s*:', body2):
            return ''                              # :root ficou vazio
        return ':root {' + body2 + '}'
    out = re.sub(r':root\s*\{([^{}]*)\}', blk, text)
    return out, removed

def rename_vars(text):
    for old, new in RENAME.items():
        text = re.sub(r'var\(--%s(?=[,)])' % re.escape(old), f'var(--{new}', text)
    return text

# ====================================================================== main
changed = {}

def save(path, new):
    old = read(path)
    if new != old:
        changed[path] = (len(old.splitlines()), len(new.splitlines()))
        write(path, new)

files = sorted(glob.glob(os.path.join(ROOT, '*.css')))
files = [f for f in files if os.path.basename(f) != 'tema.css']

for path in files:
    name = os.path.basename(path)
    t = read(path)

    # 1) renomear primary/secondary das páginas com paleta própria
    if name in RENAME_FILES:
        t = rename_vars(t)

    # 2) remover :root duplicados (as cores agora vivem no tema.css)
    t, removed = strip_root(t, name)
    if removed: stats[f':root duplicados removidos ({name})'] += len(removed)

    # 3) remover regras do modo claro VERDE (o claro agora é o roxo)
    if name != 'acessibilidade.css':
        rules = parse(t); cut = []
        for r in rules:
            parts = split_selectors(r['sel'])
            claro = [s for s in parts if 'modo-claro' in s and ':not(.modo-claro)' not in s]
            if not claro: continue
            if name == 'admin.css':               # painel admin tem tema próprio
                continue
            keep = [s for s in parts if s not in claro]
            cut.append((r, keep))
        for r, keep in reversed(cut):
            if keep:
                head = ',\n'.join(keep)
                t = t[:r['start']] + head + ' {' + t[r['bs']:r['end']]
            else:
                t = t[:r['start']] + t[r['end']:]
            stats['regras do modo claro verde removidas'] += 1
        t = re.sub(r'(?:\n[ \t]*/\*(?:(?!\*/).)*?\*/[ \t]*(?=\n\s*\n\s*/\*))+', '', t, flags=re.S) if name == 'geral.css' else t

    # 4) trocar cores repetidas por variáveis
    skip = lambda s: 'alto-contraste' in s or s.strip() == ':root' or 'modo-claro' in s and name == 'admin.css'
    if name != 'admin.css':
        t = convert_css(t, skip)

    save(path, t)

# ------------------------------------------------ painel admin (tokens próprios)
p = os.path.join(ROOT, 'admin.css'); t = read(p)
novo_root = """:root {
    color-scheme: dark;
    --admin-bg: var(--bg-main);
    --admin-surface: var(--bg-surface);
    --admin-surface-raised: var(--bg-section);
    --admin-purple: var(--bg-card);
    --admin-purple-hover: var(--bg-card-hover);
    --admin-blue: var(--primary);
    --admin-gold: var(--gold);
    --admin-gold-soft: var(--gold-light);
    --admin-text: var(--text);
    --admin-muted: var(--text-muted);
    --admin-border: rgba(var(--gold-rgb), 0.18);
    --admin-danger: #ffb2a7;
    font-family: "Poppins", sans-serif;
    color: var(--admin-text);
}"""
if '--admin-bg: var(--bg-main)' not in t:
    t, n = re.subn(r':root\s*\{\s*color-scheme: dark;.*?color: var\(--admin-text\);\s*\}', novo_root, t, count=1, flags=re.S)
    if n != 1: sys.exit('ERRO: bloco :root do admin.css não encontrado')
    stats['admin.css: :root usa tokens do tema'] += 1
    t, n = re.subn(r'body\.modo-claro \{\s*color-scheme: light;.*?body\.modo-claro \.admin-state-card \{ background: #fff; \}\s*', '', t, count=1, flags=re.S)
    if n != 1: sys.exit('ERRO: bloco modo-claro do admin.css não encontrado')
    stats['admin.css: modo claro verde removido'] += 1
save(p, t)

# ---------------------------------------------------------- ajustes pontuais
p = os.path.join(ROOT, 'geral.css'); t = read(p)

# @import do tema (tem que ser a primeira regra)
if '@import url("tema.css")' not in t:
    t = '@import url("tema.css");\n\n' + t

# bloco :root antigo (page-bg) agora vive no tema.css
t = re.sub(r'/\*\s*Cor padrão do logo[^*]*\*/\s*:root\s*\{[^{}]*\}\s*', '', t, flags=re.S)
t = re.sub(r':root\s*\{\s*--gold-light:[^{}]*\}\s*', '', t, flags=re.S)

# modo claro verde do body
t = re.sub(r'body\.modo-claro\s*\{\s*background-color:\s*var\(--bg-main\);\s*background-image:\s*none;\s*\}\s*', '', t)

# seletores que dependiam do roxo ser o "sem classe"
t = t.replace('body:not(.modo-claro):not(.alto-contraste)', 'body:not(.alto-contraste)')

# html e scrollbar seguem o tema
t = t.replace('html {\n    overflow-x: hidden;\n    background-color: #3A256A;\n}',
              'html {\n    overflow-x: hidden;\n    background-color: var(--page-bg-bottom);\n}')
t = t.replace('scrollbar-color: #DDAA41 #161928;', 'scrollbar-color: var(--gold-dark) var(--bg-main);')

if 'html:has(body.alto-contraste)' not in t:
    t = t.replace('body {\n    overflow-x: hidden;', 'html:has(body.alto-contraste) {\n    background-color: #000000;\n}\n\nbody {\n    overflow-x: hidden;', 1)

# alto contraste: novos tokens
if '--ink-rgb' not in t.split('ALTO CONTRASTE', 1)[-1][:1500]:
    t = t.replace('''    /* TEXTOS */
    --text: #FFFFFF;
    --text-soft: #FFFFFF;

}''', '''    /* TEXTOS */
    --text: #FFFFFF;
    --text-soft: #FFFFFF;
    --text-muted: #FFFFFF;
    --text-disabled: #FFFFFF;

    /* TOKENS DE TEMA (tema.css) */
    --bg-deep: #000000;
    --bg-main-rgb: 0, 0, 0;
    --bg-section-rgb: 0, 0, 0;
    --bg-card-rgb: 0, 0, 0;
    --gold-rgb: 255, 255, 0;
    --ink-rgb: 255, 255, 255;
    --on-gold: #000000;
    --border: #FFFFFF;
    --border-light: #FFFFFF;
    --border-gold: #FFFF00;
    --ui-primary: #FFFFFF;
    --ui-primary-hover: #FFFFFF;
    --ui-secondary: #FFFFFF;
    --ui-secondary-hover: #FFFFFF;
    --purple: #FFFFFF;
    --purple-dark: #FFFFFF;
    --purple-light: #FFFFFF;
    --dark-contrast: #000000;

}''', 1)
save(p, t)

# acessibilidade.css: botão de tema usa o degradê do tema
p = os.path.join(ROOT, 'acessibilidade.css'); t = read(p)
t = re.sub(r'(body\.modo-claro #alternar-tema\s*\{\s*background-image:\s*)linear-gradient\([^;]*\);',
           r'\1linear-gradient(90deg, var(--primary) 0%, var(--bg-card-hover) 50%, var(--primary) 100%);', t)
save(p, t)

# ------------------------------------------------------------------ relatório
print(('[DRY-RUN] ' if DRY else '') + 'Arquivos alterados:')
for f, (a, b) in sorted(changed.items()):
    print(f'  {f}: {a} → {b} linhas')
print('\nSubstituições:')
for k, v in sorted(stats.items(), key=lambda x: -x[1]):
    print(f'  {v:4d}  {k}')