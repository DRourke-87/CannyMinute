# CannyMinute Website + Beta Collector

## Recommended free stack
- Hosting: `GitHub Pages` (simple, stable, free, custom domain ready).
- Lead collection: `Google Apps Script` writing to `Google Sheets` (free, no backend server to run).

This combination keeps costs at zero for early launch, and gives you one CSV/Sheet to manage beta invites.

## Folder layout
- `index.html`: landing page.
- `privacy.html`: privacy summary page.
- `styles.css`: site styling.
- `app.js`: interactions and lead form submission.
- `config.js`: endpoint config (do not commit live secrets).
- `collector/google-apps-script.gs`: Apps Script backend code.
- `assets/*`: logo/favicon/OG assets.

## 1) Local preview
From `website/`:

```powershell
python -m http.server 8080
```

Open `http://localhost:8080`.

## 2) Create Google Sheet collector
1. Create a new Google Sheet named `CannyMinute Beta`.
2. Copy the Sheet ID from the URL. 
3. Go to `https://script.google.com`, create a new Apps Script project.
4. Paste contents of `collector/google-apps-script.gs`.
5. In Apps Script:
   - `Project Settings` -> `Script properties` -> add `SPREADSHEET_ID` with your Sheet ID.
6. Deploy:
   - `Deploy` -> `New deployment` -> `Web app`.
   - Execute as: `Me`.
   - Who has access: `Anyone`.
7. Copy the web app URL.

## 3) Configure landing page form
In `config.js`, set:

```js
window.CANNYMINUTE_CONFIG = {
    leadEndpointUrl: "https://script.google.com/macros/s/REPLACE_ME/exec",
    leadEndpointMode: "google-apps-script",
    useNoCors: true,
    debug: false
};
```

## 4) Deploy with GitHub Pages
1. Push this repo to GitHub.
2. In repo settings:
   - `Pages` -> Source: `GitHub Actions`.
3. Ensure workflow `.github/workflows/website-deploy.yml` exists.
4. Push to `main`; workflow deploys `website/` automatically.

## 5) Point cannyminute.com to GitHub Pages
At your domain DNS provider set:
- A record `@` -> `185.199.108.153`
- A record `@` -> `185.199.109.153`
- A record `@` -> `185.199.110.153`
- A record `@` -> `185.199.111.153`
- CNAME `www` -> `DRourke-87.github.io`

Then in GitHub Pages settings:
- Custom domain: `www.cannyminute.com`
- Enforce HTTPS: enabled.

Optional:
- Add a forward from `cannyminute.com` to `https://www.cannyminute.com`.

The `website/CNAME` file is included for this.

Important:
- On GitHub Free, Pages is supported for public repositories.
- If this repository stays private, your plan must support private-repo Pages.

## Launch best-practice checklist
- Keep form friction low: only name + email required.
- Add anti-spam honeypot (already included).
- Promise frequency: set expectation for email cadence.
- Use double opt-in for production email platform later.
- Add analytics only after consent policy is clear.
- Add one trust line near form: on-device privacy + no sale of data.
- Test on mobile first before launch (load speed + readable copy + clear CTA).

## Migration path when list grows
- Keep landing page on GitHub Pages.
- Move collector from Apps Script to:
  - Beehiiv/ConvertKit/Mailchimp API, or
  - Cloudflare Workers + KV + email provider webhook.

This keeps UX unchanged and swaps only `app.js` submit destination.
