# DocHledač – fulltextové hledání v souborech na Androidu

Vybereš složky, zaškrtneš formáty, aplikace obsah zaindexuje a hledá okamžitě při psaní –
s řádky výskytů a náhledem souboru. Česky i anglicky (podle jazyka telefonu).

## Formáty
TXT, SRT/VTT/ASS/SUB titulky, MD, CSV, LOG, JSON, XML, INI, HTML, PDF, DOCX, ODT, RTF,
EPUB, FB2, XLSX, ODS, PPTX, ODP, a s OCR i obrázky (JPG/PNG/WEBP) a naskenovaná PDF.

## Hledání
- Velikost písmen ani diakritika nevadí („zluty“ najde „Žlutý“).
- `slovo1 slovo2` – obě slova · `"přesná fráze"` · `-slovo` – vyloučit · `a | b` nebo `a OR b` – aspoň jedno
- Režimy: **Začátky slov** (bleskové) · **Kdekoli v textu** (i uprostřed slova) · **Jen názvy souborů** (jako Everything)
- Řazení podle výskytů / názvu / data (menu ⋮).
- U titulků se místo čísla řádku ukazuje čas titulku, u PDF stránka, u prezentací snímek, u tabulek list, u EPUB kapitola.

## Náhled
- Hledání přímo v souboru, Předchozí / Další výskyt, velikost písma A− / A+.
- Dlouhé podržení řádku → kopírovat / sdílet.
- U PDF a obrázků „Zobrazit stránku / obrázek“ – skutečný vzhled s přiblížením.

## Zkratky
- Podržení ikony aplikace → **Hledat** / **Aktualizovat index**.
- Označený text v jakékoli aplikaci → ⋮ → **Hledat v DocHledači** (a také ve sdílení textu).
- Widget na plochu s lištou hledání.

## Index
- Aktualizace při spuštění a volitelně na pozadí (6 h / 12 h / denně, volitelně jen při nabíjení).
- Zpracovávají se jen nové a změněné soubory; přerušená indexace pokračuje tam, kde skončila.
- Vyloučené podsložky – konkrétní složka nebo všechny složky daného názvu.

## Sestavení
Push do GitHubu → záložka **Actions** → po doběhnutí **Artifacts → DocHledac-apk**.
Nová verze se nainstaluje přes starou (stejný podpisový klíč `app/dochledac.jks`).

`docs/privacy.html` – zásady ochrany soukromí (pro Google Play).
