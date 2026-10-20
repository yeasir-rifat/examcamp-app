/**
 * Examcamp Service Worker
 *
 * Deployment-independent.
 *
 * Works on:
 * GitHub Pages
 * Vercel
 */

"use strict";

const CACHE_VERSION = "examcamp-v15";

const APP_CACHE = `${CACHE_VERSION}-app`;
const ASSET_CACHE = `${CACHE_VERSION}-assets`;


/* =========================================================
   INSTALL
   ========================================================= */

self.addEventListener("install", (event) => {

  event.waitUntil(

    caches.open(APP_CACHE)
      .then(async (cache) => {

        const scope = self.registration.scope;

        const appShell = [
          new URL("./", scope).href,
          new URL("index.html", scope).href,
          new URL("manifest.json", scope).href
        ];

        await cache.addAll(appShell);

      })
      .then(() => self.skipWaiting())

      .catch((error) => {
        console.error(
          "[Examcamp SW] Installation failed:",
          error
        );
      })

  );

});


/* =========================================================
   ACTIVATE
   ========================================================= */

self.addEventListener("activate", (event) => {

  event.waitUntil(

    caches.keys()
      .then((cacheNames) => {

        return Promise.all(

          cacheNames
            .filter((cacheName) => {

              return (
                cacheName.startsWith("examcamp-") &&
                cacheName !== APP_CACHE &&
                cacheName !== ASSET_CACHE
              );

            })
            .map((cacheName) => caches.delete(cacheName))

        );

      })
      .then(() => self.clients.claim())

  );

});


/* =========================================================
   FETCH
   ========================================================= */

self.addEventListener("fetch", (event) => {

  const request = event.request;

  if (request.method !== "GET") {
    return;
  }

  const url = new URL(request.url);

  /*
   * Only handle requests from the same origin.
   *
   * Firebase, Gemini, Google APIs, CDN resources, etc.
   * remain untouched.
   */
  if (url.origin !== self.location.origin) {
    return;
  }

  /*
   * Only intercept requests inside this Service Worker's scope.
   */
  const scope = new URL(self.registration.scope);

  if (!url.pathname.startsWith(scope.pathname)) {
    return;
  }


  /* -------------------------------------------------------
     Navigation
     Network first → cached fallback
     ------------------------------------------------------- */

  if (request.mode === "navigate") {

    event.respondWith(

      fetch(request)

        .then((response) => {

          if (response && response.ok) {

            const clone = response.clone();

            caches.open(APP_CACHE)
              .then((cache) => {
                cache.put(request, clone);
              });

          }

          return response;

        })

        .catch(async () => {

          const cachedPage =
            await caches.match(request);

          if (cachedPage) {
            return cachedPage;
          }

          const fallbackUrl =
            new URL("index.html", scope).href;

          const fallback =
            await caches.match(fallbackUrl);

          if (fallback) {
            return fallback;
          }

          return new Response(
            "Examcamp is currently offline.",
            {
              status: 503,
              headers: {
                "Content-Type":
                  "text/plain; charset=utf-8"
              }
            }
          );

        })

    );

    return;
  }


  /* -------------------------------------------------------
     Static assets
     Cache first → Network fallback
     ------------------------------------------------------- */

  event.respondWith(

    caches.match(request)

      .then((cachedResponse) => {

        if (cachedResponse) {
          return cachedResponse;
        }

        return fetch(request)

          .then((response) => {

            if (
              response &&
              response.status === 200 &&
              response.type === "basic"
            ) {

              const clone = response.clone();

              caches.open(ASSET_CACHE)
                .then((cache) => {
                  cache.put(request, clone);
                });

            }

            return response;

          })

          .catch(() => {

            return new Response(
              "",
              {
                status: 503,
                statusText: "Offline"
              }
            );

          });

      })

  );

});


/* =========================================================
   MESSAGE HANDLER
   ========================================================= */

self.addEventListener("message", (event) => {

  if (!event.data) {
    return;
  }


  if (event.data.type === "SKIP_WAITING") {
    self.skipWaiting();
  }


  if (event.data.type === "CLEAR_CACHE") {

    event.waitUntil(

      caches.keys()
        .then((cacheNames) => {

          return Promise.all(

            cacheNames
              .filter((name) =>
                name.startsWith("examcamp-")
              )
              .map((name) =>
                caches.delete(name)
              )

          );

        })

    );

  }

});
