# DocHledač – fulltextové hledání v souborech na Androidu

Vybereš složky, zaškrtneš formáty (TXT, SRT, PDF, DOCX, ODT, HTML, MD, CSV…),
aplikace obsah jednou zaindexuje a pak hledá okamžitě při psaní – s řádky výskytů
a náhledem celého souboru.

## Jak to funguje
- **Index se vytváří sám** – po přidání složky a pak při každém spuštění aplikace
  (jen nové a změněné soubory, takže to je rychlé). Ručně: ikona ⟳.
- **Hledání nerozlišuje velikost písmen ani diakritiku** („zluty“ najde „Žlutý“).
- Víc slov = soubor musí obsahovat všechna.
- Režim **Začátky slov** (výchozí, bleskový) – „hled“ najde „hledání“, „hledat“.
- Režim **Kdekoli v textu** – najde i uprostřed slova („ledá“ → „hledání“), je pomalejší.
- Klepnutí na soubor / řádek → náhled textu se zvýrazněním, tlačítka Předchozí / Další.
- Dlouhé podržení souboru (nebo menu ⋮ v náhledu) → otevřít v jiné aplikaci.
- Kódování TXT/SRT se pozná samo (UTF-8, UTF-16, windows-1250).
- U PDF se místo čísla řádku ukazuje číslo stránky. Naskenovaná PDF bez textu nejdou prohledat (chybí OCR).

## Sestavení APK přes GitHub (zdarma, bez instalace čehokoli)
1. Na github.com si založ účet a nový repozitář (klidně **Private**).
2. Nahraj do něj celý obsah této složky (i skrytou složku `.github`).
   Nejjednodušší je aplikace GitHub Desktop, nebo na webu „Add file → Upload files“ a přetáhnout složky.
3. Záložka **Actions** – build se spustí sám (cca 5 minut).
4. Po dokončení otevři běh → dole **Artifacts → DocHledac-apk** → stáhni, rozbal, nainstaluj APK do mobilu
   (povolit instalaci z neznámých zdrojů).

Aplikace je podepsaná vlastním klíčem `app/dochledac.jks`, takže novější verze se nainstaluje
přes starou a index zůstane. Klíč má veřejné heslo – repozitář proto drž soukromý.

## Alternativně Android Studio
Otevři složku jako projekt → Build → Build APK(s).
