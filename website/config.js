window.CANNYMINUTE_CONFIG = {
    // Paste your deployed Google Apps Script web app URL here.
    // Example: https://script.google.com/macros/s/AKfycb.../exec
    leadEndpointUrl: "https://script.google.com/macros/s/AKfycbwCafx2iQfYA_1MRNDwAAimoyZH__OT_RGC6S4Ip9Dl5flA0K4nGRA3VybUQfrFdAkdQg/exec",

    // Use "google-apps-script" for the supplied collector script in website/collector.
    // Use "json" only if you wire to a JSON endpoint that supports CORS.
    leadEndpointMode: "google-apps-script",

    // Apps Script endpoints are simplest with no-cors from a static site.
    useNoCors: true,

    // Set true to print form payload diagnostics in browser console.
    debug: false
};
