(function () {
    try {
        var scheme = window.localStorage.getItem('color-scheme');
        if (scheme === 'light' || scheme === 'dark') {
            document.documentElement.setAttribute('data-color-scheme', scheme);
        }
    } catch (ignored) {
        // storage may be unavailable; the device setting then applies
    }
})();
