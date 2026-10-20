    (function () {
      "use strict";

      var OPEN_CLASS = "is-open";
      var openWrap = null;
      var openListbox = null;
      var CLOSE_ANIM_MS = 220; // keep in sync with .select-listbox transition duration

      function closeOpen() {
        if (!openWrap) return;
        openWrap.classList.remove(OPEN_CLASS);
        if (openListbox) {
          // Animate out instead of yanking the listbox away instantly:
          // drop .is-visible so the CSS transition (opacity/transform)
          // reverses, then remove the element once that transition has
          // had time to finish. The listbox is already detached from
          // openWrap/openListbox below so a rapid re-open (which calls
          // closeOpen() again first) builds a fresh listbox rather than
          // touching this one mid-removal.
          var toRemove = openListbox;
          toRemove.classList.remove("is-visible");
          setTimeout(function () { toRemove.remove(); }, CLOSE_ANIM_MS);
        }
        openWrap = null;
        openListbox = null;
        document.removeEventListener("keydown", onKeyDown, true);
      }

      function labelFor(select) {
        var opt = select.options[select.selectedIndex];
        return opt ? (opt.textContent || opt.value) : "";
      }

      function positionListbox(wrap, listbox) {
        listbox.classList.remove("is-flipped");
        var rect = wrap.getBoundingClientRect();
        var spaceBelow = window.innerHeight - rect.bottom;
        var needed = Math.min(260, listbox.scrollHeight || 260);
        var flipped = spaceBelow < needed + 12 && rect.top > needed + 12;
        if (flipped) listbox.classList.add("is-flipped");
        // The listbox is appended to <body> (see buildListbox) so it can
        // escape any ancestor's `overflow: hidden/auto` — e.g. a modal
        // body, which would otherwise clip the last option or force a
        // second scrollbar to appear. Since it's no longer a child of
        // .select-wrap, position it with `fixed` + the trigger's own
        // viewport rect instead of relying on CSS `position: absolute`
        // relative to the wrap.
        listbox.style.left = rect.left + "px";
        listbox.style.width = rect.width + "px";
        if (flipped) {
          listbox.style.top = "";
          listbox.style.bottom = (window.innerHeight - rect.top + 8) + "px";
        } else {
          listbox.style.bottom = "";
          listbox.style.top = (rect.bottom + 8) + "px";
        }
      }

      function buildListbox(wrap, select, trigger) {
        var listbox = document.createElement("div");
        listbox.className = "select-listbox";
        listbox.setAttribute("role", "listbox");

        var optionEls = [];
        Array.prototype.forEach.call(select.options, function (opt, idx) {
          var item = document.createElement("div");
          item.className = "select-option";
          item.setAttribute("role", "option");
          item.textContent = opt.textContent || opt.value;
          item.dataset.index = String(idx);
          if (opt.disabled) {
            item.setAttribute("aria-disabled", "true");
            item.style.opacity = "0.5";
            item.style.cursor = "not-allowed";
          }
          if (idx === select.selectedIndex) {
            item.classList.add("is-selected");
            item.setAttribute("aria-selected", "true");
          }
          item.addEventListener("mouseenter", function () {
            optionEls.forEach(function (el) { el.classList.remove("is-active"); });
            item.classList.add("is-active");
          });
          // Prevent the trigger button from losing focus when the user
          // presses down on an option. Without this, mousedown fires a
          // native `blur`/`focusout` on the trigger BEFORE the matching
          // `click` event ever reaches this option (mousedown -> blur ->
          // mouseup -> click, in that order), and our focusout handler
          // below closes/removes the listbox in response to that blur —
          // so the option is gone from the DOM by the time `click` would
          // have fired, and the selection is silently lost. This is the
          // reason every dropdown appeared to open fine but picking an
          // option never took effect.
          item.addEventListener("mousedown", function (e) {
            e.preventDefault();
          });
          item.addEventListener("click", function () {
            if (opt.disabled) return;
            var changed = select.selectedIndex !== idx;
            select.selectedIndex = idx;
            if (changed) {
              select.dispatchEvent(new Event("input", { bubbles: true }));
              select.dispatchEvent(new Event("change", { bubbles: true }));
            }
            trigger.querySelector(".select-trigger__label").textContent = labelFor(select);
            closeOpen();
            trigger.focus();
          });
          optionEls.push(item);
          listbox.appendChild(item);
        });

        document.body.appendChild(listbox);
        positionListbox(wrap, listbox);
        // Force a style flush so the browser registers the initial
        // (scaled-down/transparent) state above before .is-visible is
        // added — otherwise both class changes land in the same paint
        // and the transition never has a "from" state to animate out of.
        // A single requestAnimationFrame is not always enough: on some
        // browsers/monitors the callback still runs before the *previous*
        // frame (the one containing the initial styles) has actually been
        // painted, so the opening/closing state changes get coalesced into
        // one paint and the listbox just pops in with no visible easing.
        // Nesting a second rAF guarantees we're scheduling the class change
        // for the frame AFTER the initial state was painted.
        void listbox.offsetHeight;
        requestAnimationFrame(function () {
          requestAnimationFrame(function () { listbox.classList.add("is-visible"); });
        });

        var activeEl = optionEls[select.selectedIndex];
        if (activeEl) {
          activeEl.classList.add("is-active");
          // Scroll the current value into view without moving the page.
          var itemTop = activeEl.offsetTop;
          var itemBottom = itemTop + activeEl.offsetHeight;
          if (itemBottom > listbox.clientHeight || itemTop < 0) {
            listbox.scrollTop = itemTop - listbox.clientHeight / 2;
          }
        }

        return { listbox: listbox, optionEls: optionEls };
      }

      function onKeyDown(e) {
        if (!openWrap) return;
        var select = openWrap.querySelector("select.form-control");
        var listbox = openListbox;
        if (!select || !listbox) return;
        var options = Array.prototype.slice.call(listbox.querySelectorAll(".select-option"));
        var activeIdx = options.findIndex(function (el) { return el.classList.contains("is-active"); });

        if (e.key === "Escape") {
          e.preventDefault();
          closeOpen();
          openWrap && openWrap.querySelector(".select-trigger").focus();
          return;
        }
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          if (activeIdx >= 0) options[activeIdx].click();
          return;
        }
        if (e.key === "ArrowDown" || e.key === "ArrowUp") {
          e.preventDefault();
          if (!options.length) return;
          var next = activeIdx;
          do {
            next = e.key === "ArrowDown"
              ? Math.min(options.length - 1, next + 1)
              : Math.max(0, next - 1);
          } while (options[next].getAttribute("aria-disabled") === "true" && next > 0 && next < options.length - 1);
          options.forEach(function (el) { el.classList.remove("is-active"); });
          options[next].classList.add("is-active");
          options[next].scrollIntoView({ block: "nearest" });
        }
      }

      function openSelect(wrap, select, trigger) {
        if (openWrap === wrap) return;
        closeOpen();
        wrap.classList.add(OPEN_CLASS);
        var built = buildListbox(wrap, select, trigger);
        openWrap = wrap;
        openListbox = built.listbox;
        document.addEventListener("keydown", onKeyDown, true);
      }

      function enhance(select) {
        if (!select || select.dataset.enhanced === "true") return;
        // Respect selects that opt out (e.g. if a future page needs the
        // native picker for a platform-specific reason).
        if (select.hasAttribute("data-no-enhance")) return;

        select.dataset.enhanced = "true";

        var wrap = document.createElement("div");
        wrap.className = "select-wrap";
        // Preserve any inline sizing already set on the <select> itself
        // (e.g. the "width: auto; min-width: 220px" exam picker) on the
        // wrapper instead, so layout doesn't shift.
        if (select.style.width) wrap.style.width = select.style.width;
        if (select.style.minWidth) wrap.style.minWidth = select.style.minWidth;
        if (select.style.maxWidth) wrap.style.maxWidth = select.style.maxWidth;

        select.parentNode.insertBefore(wrap, select);
        wrap.appendChild(select);
        wrap.classList.add("is-enhanced");

        var trigger = document.createElement("button");
        trigger.type = "button";
        trigger.className = "select-trigger";
        trigger.setAttribute("aria-haspopup", "listbox");
        trigger.setAttribute("aria-expanded", "false");
        if (select.id) trigger.setAttribute("aria-labelledby", select.id + "-label-proxy");

        var labelSpan = document.createElement("span");
        labelSpan.className = "select-trigger__label";
        labelSpan.textContent = labelFor(select);

        var chevron = document.createElementNS("http://www.w3.org/2000/svg", "svg");
        chevron.setAttribute("class", "select-trigger__chevron");
        chevron.setAttribute("viewBox", "0 0 24 24");
        chevron.setAttribute("fill", "none");
        chevron.setAttribute("stroke", "currentColor");
        chevron.setAttribute("stroke-width", "2");
        chevron.setAttribute("stroke-linecap", "round");
        chevron.setAttribute("stroke-linejoin", "round");
        var polyline = document.createElementNS("http://www.w3.org/2000/svg", "polyline");
        polyline.setAttribute("points", "6 9 12 15 18 9");
        chevron.appendChild(polyline);

        trigger.appendChild(labelSpan);
        trigger.appendChild(chevron);
        wrap.appendChild(trigger);

        function syncDisabled() {
          wrap.classList.toggle("is-disabled", select.disabled);
          trigger.disabled = select.disabled;
        }
        syncDisabled();

        function syncLabel() {
          labelSpan.textContent = labelFor(select);
        }

        // Keep the trigger label in sync no matter how the underlying
        // <select> is changed elsewhere in the app: a user click (via our
        // own handler below), a plain `change` event some other listener
        // dispatches, code setting `.value`/`.selectedIndex` directly, or
        // code that rebuilds the options list entirely with
        // `select.innerHTML = "..."` (e.g. populateGeminiModelOptions(),
        // the admin Subject dropdown, i18n re-renders). None of those last
        // three fire a `change` event, and the innerHTML case also changes
        // which options even exist, so a MutationObserver watching the
        // select's children/attributes is the only thing that catches
        // all of them reliably.
        var mo = new MutationObserver(function (mutations) {
          syncDisabled();
          syncLabel();
        });
        mo.observe(select, {
          attributes: true,
          attributeFilter: ["disabled"],
          childList: true,
          subtree: true,
          characterData: true,
        });
        select.addEventListener("change", syncLabel);
        select.addEventListener("input", syncLabel);

        trigger.addEventListener("click", function () {
          if (select.disabled) return;
          if (wrap.classList.contains(OPEN_CLASS)) {
            closeOpen();
          } else {
            openSelect(wrap, select, trigger);
            trigger.setAttribute("aria-expanded", "true");
          }
        });

        wrap.addEventListener("focusout", function (e) {
          // Close if focus is moving outside this whole wrap (trigger or
          // listbox items), but not when it's just moving between them.
          //
          // IMPORTANT: this fires whenever THIS wrap loses focus — including
          // when the user clicks straight from this trigger to a DIFFERENT
          // select's trigger. In that case the other trigger's click handler
          // synchronously opens the other wrap and reassigns the module-level
          // `openWrap` *before* this rAF callback runs, so blindly calling
          // closeOpen() here would tear down the OTHER select's listbox that
          // was just opened, not this one. Guarding on `openWrap === wrap`
          // ensures we only ever close ourselves, never a select opened after us.
          requestAnimationFrame(function () {
            if (openWrap === wrap && !wrap.contains(document.activeElement)) closeOpen();
          });
        });
      }

      function enhanceAll(root) {
        var scope = root || document;
        var selects = scope.querySelectorAll ? scope.querySelectorAll("select.form-control") : [];
        Array.prototype.forEach.call(selects, enhance);
      }

      document.addEventListener("click", function (e) {
        if (openWrap && !openWrap.contains(e.target) && !(openListbox && openListbox.contains(e.target))) closeOpen();
      });
      window.addEventListener("resize", function () {
        if (openWrap && openListbox) {
          positionListbox(openWrap, openListbox);
        }
      });
      window.addEventListener("scroll", function (e) {
        // Capture-phase listener so we still hear page/ancestor scrolls
        // even though `scroll` doesn't bubble — but that also means a
        // scroll started *inside the open listbox itself* reaches here
        // during capture, which would close the dropdown the instant the
        // user tried to scroll through its options. Ignore scrolls whose
        // target is the open listbox (or something inside it).
        if (openListbox && e.target && openListbox.contains(e.target)) return;
        closeOpen();
      }, true);

      // Enhance whatever already exists, then keep watching for selects
      // added later (settings/admin panels, modals built on demand, i18n
      // re-renders that recreate nodes, etc.).
      function boot() {
        enhanceAll(document);
        var observer = new MutationObserver(function (mutations) {
          mutations.forEach(function (m) {
            m.addedNodes && m.addedNodes.forEach(function (node) {
              if (node.nodeType !== 1) return;
              if (node.matches && node.matches("select.form-control")) enhance(node);
              if (node.querySelectorAll) enhanceAll(node);
            });
          });
        });
        observer.observe(document.body, { childList: true, subtree: true });
      }

      if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", boot);
      } else {
        boot();
      }
    })();
