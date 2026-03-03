/**
 * CannyMinute beta lead collector for Google Apps Script.
 *
 * Setup:
 * 1) Open script.google.com and create a new Apps Script project.
 * 2) Replace default code with this file.
 * 3) In Script Properties, set SPREADSHEET_ID to the target Google Sheet id.
 * 4) Deploy > New deployment > Web app:
 *    - Execute as: Me
 *    - Who has access: Anyone
 * 5) Copy deployed URL into website/config.js leadEndpointUrl.
 */
const SHEET_NAME = "BetaLeads";

function doPost(e) {
  try {
    const payload = parsePayload_(e);
    if (payload.company) {
      return json_({ ok: true, ignored: true });
    }
    if (!isValidEmail_(payload.email)) {
      return json_({ ok: false, error: "Invalid email" });
    }

    const sheet = getSheet_();
    sheet.appendRow([
      new Date(),
      payload.firstName,
      payload.email,
      payload.shopStyle,
      payload.source,
      payload.alphaTester ? "yes" : "no",
      payload.userAgent,
      payload.submittedAtUtc,
      payload.country,
      payload.ipHint
    ]);

    return json_({ ok: true });
  } catch (error) {
    return json_({ ok: false, error: String(error) });
  }
}

function parsePayload_(e) {
  const p = (e && e.parameter) || {};
  return {
    firstName: safe_(p.firstName),
    email: safe_(p.email).toLowerCase(),
    shopStyle: safe_(p.shopStyle),
    source: safe_(p.source || "cannyminute_landing"),
    alphaTester: safe_(p.alphaTester) === "true" || safe_(p.alphaTester) === "on",
    company: safe_(p.company),
    userAgent: safe_(p.userAgent),
    submittedAtUtc: safe_(p.submittedAtUtc),
    country: safe_(p.country),
    ipHint: safe_(p.ipHint)
  };
}

function safe_(value) {
  return value ? String(value).trim().slice(0, 512) : "";
}

function isValidEmail_(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function getSheet_() {
  const spreadsheetId = PropertiesService.getScriptProperties().getProperty("SPREADSHEET_ID");
  if (!spreadsheetId) {
    throw new Error("Missing SPREADSHEET_ID script property");
  }
  const spreadsheet = SpreadsheetApp.openById(spreadsheetId);
  const sheet = spreadsheet.getSheetByName(SHEET_NAME) || spreadsheet.insertSheet(SHEET_NAME);
  if (sheet.getLastRow() === 0) {
    sheet.appendRow([
      "capturedAt",
      "firstName",
      "email",
      "shopStyle",
      "source",
      "alphaTester",
      "userAgent",
      "submittedAtUtc",
      "country",
      "ipHint"
    ]);
  }
  return sheet;
}

function json_(payload) {
  return ContentService
    .createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);
}
