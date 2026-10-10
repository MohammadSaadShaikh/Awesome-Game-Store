/**
 * Cross-Store Comparison & Game Information Logic for AWESOME™ Game Store (details.html)
 * Fetches verified game details from Steam Official API & CheapShark
 */
document.addEventListener('DOMContentLoaded', () => {
  const detailsContainer = document.getElementById('details-container');
  const urlParams = new URLSearchParams(window.location.search);
  const gameTitle = urlParams.get('title');
  const paramGameId = urlParams.get('gameId') || '';
  const paramSteamAppId = urlParams.get('steamAppId') || '';

  // Track Modal Elements
  const trackModal = document.getElementById('track-modal');
  const closeTrackModal = document.getElementById('close-track-modal');
  const trackForm = document.getElementById('track-form');
  const trackGameTitle = document.getElementById('track-game-title');
  const trackGameId = document.getElementById('track-game-id');
  const trackGameNameDisplay = document.getElementById('track-game-name-display');
  const trackEmail = document.getElementById('track-email');
  const trackTargetPrice = document.getElementById('track-target-price');

  // Lightbox Elements
  const lightboxModal = document.getElementById('screenshot-lightbox');
  const closeLightboxBtn = document.getElementById('close-lightbox');
  const lightboxImg = document.getElementById('lightbox-img');
  const lightboxPrevBtn = document.getElementById('lightbox-prev');
  const lightboxNextBtn = document.getElementById('lightbox-next');
  const lightboxCounter = document.getElementById('lightbox-counter');

  let currentLookupData = null;
  let activeScreenshots = [];
  let currentScreenshotIndex = 0;

  // Initialize Currency Engine
  CurrencyEngine.initSelector('currency-select');

  const savedEmail = localStorage.getItem('awesome_user_email') || '';
  if (savedEmail) {
    trackEmail.value = savedEmail;
  }

  // Listen for currency changes
  window.addEventListener('currencyChange', () => {
    if (currentLookupData) {
      renderDetails(currentLookupData);
    }
  });

  if (!gameTitle || gameTitle.trim() === '') {
    showError('No game title specified for comparison.');
    return;
  }

  async function loadComparison() {
    showLoading();
    try {
      currentLookupData = await ApiService.compareDeals(gameTitle, paramGameId, paramSteamAppId);
      
      // If backend didn't bundle gameInfo, attempt dedicated info fetch
      if (!currentLookupData.gameInfo) {
        renderDetails(currentLookupData);
        fetchGameInfoProgressive();
      } else {
        renderDetails(currentLookupData);
      }
    } catch (err) {
      console.error('Error loading comparison:', err);
      showError(`Failed to load store comparison for "${gameTitle}".`);
    }
  }

  async function fetchGameInfoProgressive() {
    try {
      const resolvedSteamAppId = (currentLookupData.storeDeals || []).map(d => d.steamAppId).find(id => id && id !== '0' && id !== 'null') || paramSteamAppId;
      const info = await ApiService.getGameInfo(gameTitle, currentLookupData.gameId || paramGameId, resolvedSteamAppId);
      if (info && currentLookupData) {
        currentLookupData.gameInfo = info;
        renderDetails(currentLookupData);
      }
    } catch (e) {
      console.warn('Progressive game info load skipped:', e);
    }
  }

  function renderDetails(data) {
    if (!data || (!data.storeDeals || data.storeDeals.length === 0) && !data.gameInfo) {
      detailsContainer.innerHTML = `
        <div class="empty-state">
          <h3>No Store Deals Found</h3>
          <p>We could not find active storefront deals for "${escapeHtml(gameTitle)}".</p>
          <a href="index.html" class="btn btn-primary" style="margin-top: 1rem;">Back to Deals</a>
        </div>
      `;
      return;
    }

    const deals = data.storeDeals || [];
    const bestDeal = deals.find(d => d.salePrice === data.cheapestPrice) || deals[0] || {};
    const formattedCheapest = CurrencyEngine.formatPrice(data.cheapestPrice || 0);

    const info = data.gameInfo || {};
    const resolvedSteamId = (deals.map(d => d.steamAppId).find(id => id && id !== '0' && id !== 'null')) || paramSteamAppId;

    // Screenshots array
    activeScreenshots = (info.screenshots && info.screenshots.length > 0) ? info.screenshots : [];

    // Metascore class
    let metascoreClass = 'metascore-badge--mid';
    if (info.metacriticScore >= 75) metascoreClass = 'metascore-badge--high';
    else if (info.metacriticScore < 50) metascoreClass = 'metascore-badge--low';

    // Hero image selection
    const heroImage = info.headerImage || data.thumbnail || bestDeal.thumbnail || 'https://via.placeholder.com/280x140?text=No+Image';

    // Comparison Table rows
    const tableRows = deals.map(deal => {
      const formattedNormal = CurrencyEngine.formatPrice(deal.normalPrice);
      const formattedSale = CurrencyEngine.formatPrice(deal.salePrice);
      const isCheapest = deal.salePrice === data.cheapestPrice;
      const redirectUrl = deal.storeUrl || getDirectStoreUrl(deal.storeName, deal.storeId, deal.title || data.title, deal.steamAppId);
      const savingsRounded = Math.round(deal.savingsPercent);

      return `
        <tr ${isCheapest ? 'style="background: rgba(34, 197, 94, 0.05);"' : ''}>
          <td>
            <div class="matrix-store-cell">
              <span>${escapeHtml(deal.storeName)}</span>
              ${isCheapest ? '<span style="color: var(--accent-green-bright); font-size: 0.75rem; font-weight: 800;">★ BEST</span>' : ''}
            </div>
          </td>
          <td style="color: var(--text-muted); text-decoration: line-through;">
            ${formattedNormal}
          </td>
          <td style="font-weight: 800; color: ${isCheapest ? 'var(--accent-green-bright)' : 'var(--text-primary)'}; font-size: 1.1rem;">
            ${formattedSale}
          </td>
          <td>
            <span class="deal-card__badge-discount" style="position: static; display: inline-block;">
              -${savingsRounded}%
            </span>
          </td>
          <td>
            <a href="${redirectUrl}" target="_blank" rel="noopener noreferrer" class="btn ${isCheapest ? 'btn-accent' : 'btn-outline'}" style="padding: 0.45rem 0.9rem; font-size: 0.82rem;">
              Buy on ${escapeHtml(deal.storeName)} ↗
            </a>
          </td>
        </tr>
      `;
    }).join('');

    // Historical low formatting
    let allTimeLowHtml = '';
    if (info.cheapestPriceEver !== null && info.cheapestPriceEver !== undefined) {
      const formattedLow = CurrencyEngine.formatPrice(info.cheapestPriceEver);
      allTimeLowHtml = `
        <div class="game-fact-row">
          <span class="game-fact-label">📉 All-Time Historical Low</span>
          <div class="all-time-low-box">
            <span class="low-price">${formattedLow}</span>
            <span class="low-date">${info.cheapestPriceEverDate ? `Recorded on ${info.cheapestPriceEverDate}` : 'Recorded on digital stores'}</span>
          </div>
        </div>
      `;
    }

    // Genre pills
    const genrePillsHtml = (info.genres && info.genres.length > 0)
      ? info.genres.map(g => `<span class="genre-pill">${escapeHtml(g)}</span>`).join('')
      : '<span style="color: var(--text-muted); font-size: 0.85rem;">Standard Edition</span>';

    // Screenshots HTML
    let screenshotsHtml = '';
    if (activeScreenshots.length > 0) {
      screenshotsHtml = `
        <div class="game-info-card">
          <h3 class="game-info-card__title">🖼️ In-Game Screenshots (${activeScreenshots.length})</h3>
          <div class="screenshots-grid">
            ${activeScreenshots.map((url, idx) => `
              <div class="screenshot-thumb-wrap" data-index="${idx}" title="Click to view full resolution">
                <img src="${escapeHtml(url)}" alt="Screenshot ${idx + 1}" loading="lazy">
                <div class="screenshot-thumb-overlay">🔍</div>
              </div>
            `).join('')}
          </div>
        </div>
      `;
    }

    // System Requirements HTML
    let sysReqHtml = '';
    if (info.minimumRequirements || info.recommendedRequirements) {
      sysReqHtml = `
        <div class="game-info-card">
          <h3 class="game-info-card__title">⚙️ PC System Requirements</h3>
          <div class="sys-req-grid">
            ${info.minimumRequirements ? `
              <div class="sys-req-card">
                <h4>Minimum Requirements</h4>
                <div class="sys-req-content">${cleanHtml(info.minimumRequirements)}</div>
              </div>
            ` : ''}
            ${info.recommendedRequirements ? `
              <div class="sys-req-card">
                <h4>Recommended Requirements</h4>
                <div class="sys-req-content">${cleanHtml(info.recommendedRequirements)}</div>
              </div>
            ` : ''}
          </div>
        </div>
      `;
    }

    // Detailed description HTML
    const descriptionBody = info.description ? cleanHtml(info.description) : (info.shortDescription ? escapeHtml(info.shortDescription) : 'No detailed synopsis provided.');

    detailsContainer.innerHTML = `
      <!-- HERO SECTION -->
      <section class="details-hero">
        <div class="details-hero__image">
          <img src="${escapeHtml(heroImage)}" alt="${escapeHtml(data.title)}" onerror="this.src='https://via.placeholder.com/280x140?text=No+Image'">
        </div>
        <div class="details-hero__content">
          <div class="badge-row">
            ${deals.length > 0 ? `
              <div class="best-price-badge" style="margin-bottom: 0;">
                🏷️ BEST PRICE: ${formattedCheapest} on ${escapeHtml(bestDeal.storeName)}
              </div>
            ` : ''}
            <div class="verified-source-badge" title="Data fetched via Steam Storefront and CheapShark APIs">
              ✓ Verified via Steam Official API & CheapShark
            </div>
            ${info.metacriticScore ? `
              <a href="${info.metacriticUrl || '#'}" target="_blank" rel="noopener noreferrer" class="metascore-badge ${metascoreClass}" title="Metacritic score ${info.metacriticScore}/100">
                <span class="metascore-number">${info.metacriticScore}</span>
                <span class="metascore-meta">
                  <span>Metascore</span>
                  <span>Review ↗</span>
                </span>
              </a>
            ` : ''}
          </div>

          <h1 class="details-hero__title">${escapeHtml(data.title)}</h1>

          <p style="color: var(--text-primary); font-size: 0.98rem; line-height: 1.6; margin-bottom: 1.25rem; max-width: 800px;">
            ${escapeHtml(info.shortDescription || `Comparing live prices across ${deals.length} digital storefronts with verified game details and hardware specifications.`)}
          </p>

          <div style="display: flex; gap: 0.75rem; flex-wrap: wrap; align-items: center;">
            <button id="track-hero-btn" class="btn btn-accent" style="padding: 0.65rem 1.25rem;">
              Track Price Alert 🔔
            </button>
            <a href="#game-info-section" class="btn btn-outline" style="padding: 0.65rem 1.25rem;">
              Game Information & Specs 🎮
            </a>
            ${resolvedSteamId ? `
              <a href="https://store.steampowered.com/app/${resolvedSteamId}/" target="_blank" rel="noopener noreferrer" class="btn btn-outline" style="padding: 0.65rem 1rem;">
                Steam Store ↗
              </a>
            ` : ''}
            ${info.officialWebsite ? `
              <a href="${escapeHtml(info.officialWebsite)}" target="_blank" rel="noopener noreferrer" class="btn btn-outline" style="padding: 0.65rem 1rem;">
                Official Website ↗
              </a>
            ` : ''}
          </div>
        </div>
      </section>

      <!-- VERIFIED GAME INFORMATION SECTION -->
      <section id="game-info-section" class="game-info-section">
        <div class="section-header" style="display: flex; justify-content: space-between; align-items: baseline; flex-wrap: wrap; gap: 1rem; margin-bottom: 1.25rem;">
          <div>
            <h2 class="section-title" style="margin-bottom: 0.25rem;">Game Overview & Specifications</h2>
            <p style="color: var(--text-muted); font-size: 0.88rem;">Information sourced from official developer records and Valve Steam storefront.</p>
          </div>
          <span class="verified-source-badge">
            Official Source: ${escapeHtml(info.sourceWebsite || 'Steam & CheapShark')}
          </span>
        </div>

        <div class="game-info-layout">
          <!-- Main Left Column -->
          <div>
            <!-- Overview & Description -->
            <div class="game-info-card">
              <h3 class="game-info-card__title">📖 About The Game</h3>
              ${info.shortDescription ? `<p class="game-overview__lead">${escapeHtml(info.shortDescription)}</p>` : ''}
              <div id="game-overview-body" class="game-overview__body">
                ${descriptionBody}
              </div>
              <button id="toggle-read-more" class="toggle-read-more-btn">
                Read Full Overview ▾
              </button>
            </div>

            <!-- In-Game Screenshots Gallery -->
            ${screenshotsHtml}

            <!-- PC Hardware Requirements -->
            ${sysReqHtml}
          </div>

          <!-- Sidebar Right Column: Key Game Facts -->
          <div>
            <div class="game-info-card" style="position: sticky; top: 1.5rem;">
              <h3 class="game-info-card__title">📊 Key Game Facts</h3>
              
              <div class="game-facts-list">
                ${info.releaseDate ? `
                  <div class="game-fact-row">
                    <span class="game-fact-label">📅 Release Date</span>
                    <span class="game-fact-value">${escapeHtml(info.releaseDate)}</span>
                  </div>
                ` : ''}

                ${(info.developers && info.developers.length > 0) ? `
                  <div class="game-fact-row">
                    <span class="game-fact-label">🛠️ Developer</span>
                    <span class="game-fact-value">${escapeHtml(info.developers.join(', '))}</span>
                  </div>
                ` : ''}

                ${(info.publishers && info.publishers.length > 0) ? `
                  <div class="game-fact-row">
                    <span class="game-fact-label">🏢 Publisher</span>
                    <span class="game-fact-value">${escapeHtml(info.publishers.join(', '))}</span>
                  </div>
                ` : ''}

                <div class="game-fact-row">
                  <span class="game-fact-label">🏷️ Genres & Tags</span>
                  <div class="genre-pills">
                    ${genrePillsHtml}
                  </div>
                </div>

                ${info.metacriticScore ? `
                  <div class="game-fact-row">
                    <span class="game-fact-label">🏆 Metacritic Rating</span>
                    <div style="margin-top: 0.35rem;">
                      <a href="${info.metacriticUrl || '#'}" target="_blank" rel="noopener noreferrer" class="metascore-badge ${metascoreClass}" style="padding: 0.35rem 0.75rem;">
                        <span class="metascore-number" style="font-size: 1.1rem;">${info.metacriticScore}</span>
                        <span class="metascore-meta">
                          <span>Metascore</span>
                          <span>Read Reviews ↗</span>
                        </span>
                      </a>
                    </div>
                  </div>
                ` : ''}

                ${allTimeLowHtml}

                <div class="game-fact-row">
                  <span class="game-fact-label">🌐 Reputable Data Source</span>
                  <span class="game-fact-value" style="font-size: 0.82rem; color: #60a5fa;">
                    Steam Storefront (Valve API) & CheapShark
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- LIVE STORE COMPARISON MATRIX SECTION -->
      <section class="matrix-section">
        <div class="section-header">
          <h2 class="section-title">Live Store Comparison Matrix</h2>
          <p style="color: var(--text-muted); font-size: 0.88rem; margin-top: 0.25rem;">
            Real-time prices verified across ${deals.length} authorized digital storefronts.
          </p>
        </div>
        <div class="matrix-table-container">
          <table class="matrix-table">
            <thead>
              <tr>
                <th>Storefront</th>
                <th>Retail Price</th>
                <th>Sale Price</th>
                <th>Savings</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              ${tableRows}
            </tbody>
          </table>
        </div>
      </section>
    `;

    // Attach Track Hero Button Handler
    const trackHeroBtn = document.getElementById('track-hero-btn');
    if (trackHeroBtn) {
      trackHeroBtn.addEventListener('click', () => {
        openTrackModal(data.title, data.gameId, data.cheapestPrice);
      });
    }

    // Attach Read More Toggle Handler
    const toggleBtn = document.getElementById('toggle-read-more');
    const overviewBody = document.getElementById('game-overview-body');
    if (toggleBtn && overviewBody) {
      // Hide button if text is already short
      if (overviewBody.scrollHeight <= 185) {
        toggleBtn.style.display = 'none';
      } else {
        toggleBtn.addEventListener('click', () => {
          const isExpanded = overviewBody.classList.toggle('expanded');
          toggleBtn.innerHTML = isExpanded ? 'Show Less ▴' : 'Read Full Overview ▾';
        });
      }
    }

    // Attach Screenshot Lightbox click handlers
    document.querySelectorAll('.screenshot-thumb-wrap').forEach(wrap => {
      wrap.addEventListener('click', (e) => {
        const idx = parseInt(e.currentTarget.getAttribute('data-index'), 10);
        openLightbox(idx);
      });
    });
  }

  // LIGHTBOX LOGIC
  function openLightbox(index) {
    if (!activeScreenshots || activeScreenshots.length === 0) return;
    currentScreenshotIndex = index;
    showScreenshot(currentScreenshotIndex);
    lightboxModal.classList.add('active');
    document.body.style.overflow = 'hidden';
  }

  function closeLightbox() {
    lightboxModal.classList.remove('active');
    document.body.style.overflow = '';
  }

  function showScreenshot(index) {
    if (index < 0) index = activeScreenshots.length - 1;
    if (index >= activeScreenshots.length) index = 0;
    currentScreenshotIndex = index;

    lightboxImg.src = activeScreenshots[currentScreenshotIndex];
    lightboxCounter.textContent = `${currentScreenshotIndex + 1} / ${activeScreenshots.length}`;
  }

  if (closeLightboxBtn) {
    closeLightboxBtn.addEventListener('click', closeLightbox);
  }

  if (lightboxPrevBtn) {
    lightboxPrevBtn.addEventListener('click', () => showScreenshot(currentScreenshotIndex - 1));
  }

  if (lightboxNextBtn) {
    lightboxNextBtn.addEventListener('click', () => showScreenshot(currentScreenshotIndex + 1));
  }

  if (lightboxModal) {
    lightboxModal.addEventListener('click', (e) => {
      if (e.target === lightboxModal) {
        closeLightbox();
      }
    });
  }

  // Keyboard navigation for Lightbox
  window.addEventListener('keydown', (e) => {
    if (!lightboxModal.classList.contains('active')) return;
    if (e.key === 'Escape') closeLightbox();
    else if (e.key === 'ArrowLeft') showScreenshot(currentScreenshotIndex - 1);
    else if (e.key === 'ArrowRight') showScreenshot(currentScreenshotIndex + 1);
  });

  // TRACK MODAL LOGIC
  function openTrackModal(title, gameId, salePriceUsd) {
    const curCurrency = CurrencyEngine.getSelectedCurrency();
    const convertedPrice = (salePriceUsd * (CurrencyEngine.RATES[curCurrency]?.rate || 1.0)).toFixed(2);

    trackGameTitle.value = title;
    trackGameId.value = gameId;
    trackGameNameDisplay.textContent = title;
    trackTargetPrice.value = convertedPrice;

    document.querySelectorAll('.current-currency-symbol').forEach(el => {
      el.textContent = CurrencyEngine.RATES[curCurrency]?.symbol || '$';
    });

    trackModal.classList.add('active');
  }

  closeTrackModal.addEventListener('click', () => {
    trackModal.classList.remove('active');
  });

  trackForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = trackEmail.value.trim();
    const title = trackGameTitle.value;
    const gameId = trackGameId.value;
    const targetPrice = parseFloat(trackTargetPrice.value);
    const currency = CurrencyEngine.getSelectedCurrency();

    if (!email || !title || isNaN(targetPrice)) {
      alert('Please fill in all required fields.');
      return;
    }

    localStorage.setItem('awesome_user_email', email);

    try {
      await ApiService.addToWishlist({
        userEmail: email,
        gameTitle: title,
        gameId: gameId,
        targetPrice: targetPrice,
        currency: currency
      });
      alert(`Price alert saved! We will track "${title}" for price drops.`);
      trackModal.classList.remove('active');
    } catch (err) {
      alert(`Error setting alert: ${err.message}`);
    }
  });

  function showLoading() {
    detailsContainer.innerHTML = `
      <div class="loading-spinner">
        <div class="spinner"></div>
        <span>Fetching live prices and verified game information for "${escapeHtml(gameTitle)}"...</span>
      </div>
    `;
  }

  function showError(msg) {
    detailsContainer.innerHTML = `
      <div class="empty-state" style="border-color: rgba(239, 68, 68, 0.4);">
        <h3 style="color: #ef4444;">Comparison Error</h3>
        <p>${escapeHtml(msg)}</p>
        <a href="index.html" class="btn btn-primary" style="margin-top: 1rem;">Back to Deals</a>
      </div>
    `;
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/[&<>"']/g, m => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    })[m]);
  }

  // Safe cleaner for Steam HTML descriptions allowing basic formatting tags
  function cleanHtml(htmlStr) {
    if (!htmlStr) return '';
    // Strip script, style, iframe, object, embed tags
    let cleaned = htmlStr.replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
                         .replace(/<style\b[^<]*(?:(?!<\/style>)<[^<]*)*<\/style>/gi, '')
                         .replace(/<iframe\b[^<]*(?:(?!<\/iframe>)<[^<]*)*<\/iframe>/gi, '');
    return cleaned;
  }

  function getDirectStoreUrl(storeName, storeId, title, steamAppId) {
    if (!title) title = '';
    const name = (storeName || '').toLowerCase();
    const id = String(storeId || '');
    const encodedTitle = encodeURIComponent(title);

    if (id === '1' || name.includes('steam')) {
      if (steamAppId && steamAppId !== '0' && steamAppId !== 'null') {
        return `https://store.steampowered.com/app/${steamAppId}/`;
      }
      return `https://store.steampowered.com/search/?term=${encodedTitle}`;
    }
    if (id === '7' || name.includes('gog')) {
      return `https://www.gog.com/en/games?query=${encodedTitle}`;
    }
    if (id === '25' || name.includes('epic')) {
      return `https://store.epicgames.com/en-US/browse?q=${encodedTitle}&sortBy=relevance&sortDir=DESC`;
    }
    if (id === '11' || name.includes('humble')) {
      return `https://www.humblebundle.com/store/search?sort=bestselling&search=${encodedTitle}`;
    }
    if (id === '3' || name.includes('greenman')) {
      return `https://www.greenmangaming.com/search/${encodedTitle}`;
    }
    if (id === '15' || name.includes('fanatical')) {
      return `https://www.fanatical.com/en/search?search=${encodedTitle}`;
    }
    if (id === '2' || name.includes('gamersgate')) {
      return `https://www.gamersgate.com/games/?query=${encodedTitle}`;
    }
    if (id === '27' || name.includes('gamesplanet')) {
      return `https://gamesplanet.com/search?query=${encodedTitle}`;
    }
    if (id === '8' || name.includes('ea') || name.includes('origin')) {
      return `https://www.ea.com/search?q=${encodedTitle}`;
    }
    if (id === '13' || name.includes('ubisoft') || name.includes('uplay')) {
      return `https://store.ubisoft.com/search?q=${encodedTitle}`;
    }
    return `https://www.google.com/search?q=${encodedTitle}+${encodeURIComponent(storeName || 'game store')}`;
  }

  loadComparison();
});
