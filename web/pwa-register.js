/**
 * Examcamp PWA Registration
 * Deployment-independent configuration.
 *
 * Works with:
 * GitHub Pages:
 * https://yeasir-rifat.github.io/examcamp/
 *
 * Vercel:
 * https://examcamp.vercel.app/
 */

(() => {
  "use strict";

  if (!("serviceWorker" in navigator)) {
    console.info("[Examcamp PWA] Service Worker is not supported.");
    return;
  }

  window.addEventListener("load", async () => {
    try {
      /*
       * Relative path.
       *
       * GitHub Pages:
       * /examcamp/sw.js
       *
       * Vercel:
       * /sw.js
       */
      const swUrl = new URL("sw.js", window.location.href);

      /*
       * Scope = current application directory.
       */
      const scopeUrl = new URL("./", window.location.href);

      const registration =
        await navigator.serviceWorker.register(swUrl.pathname, {
          scope: scopeUrl.pathname,
          updateViaCache: "none"
        });

      console.info(
        "[Examcamp PWA] Service Worker registered:",
        registration.scope
      );

      await registration.update().catch(() => {});

    } catch (error) {
      console.error(
        "[Examcamp PWA] Service Worker registration failed:",
        error
      );
    }
  });

  window.ExamcampPWA = {

    isStandalone() {
      return (
        window.matchMedia("(display-mode: standalone)").matches ||
        window.navigator.standalone === true
      );
    },

    async update() {
      try {
        const registration =
          await navigator.serviceWorker.getRegistration();

        if (registration) {
          await registration.update();
          return true;
        }

        return false;

      } catch (error) {
        console.error(
          "[Examcamp PWA] Update check failed:",
          error
        );

        return false;
      }
    }
  };

})();
