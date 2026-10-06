/**
 * Cross-Store Comparison Matrix Logic for AWESOME™ Game Store (details.html)
 */
document.addEventListener('DOMContentLoaded', () => {
  const detailsContainer = document.getElementById('details-container');
  const urlParams = new URLSearchParams(window.location.search);
  const gameTitle = urlParams.get('title');

  const trackModal = document.getElementById('track-modal');
  const closeTrackModal = document.getElementById('close-track-modal');
  const trackForm = document.getElementById('track-form');
  const trackGameTitle = document.getElementById('track-game-title');
  const trackGameId = document.getElementById('track-game-id');
  const trackGameNameDisplay = document.getElementById('track-game-name-display');
  const trackEmail = document.getElementById('track-email');
  const trackTargetPrice = document.getElementById('track-target-price');

  let currentLookupData = null;

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
      currentLookupData = await ApiService.compareDeals(gameTitle);
      renderDetails(currentLookupData);
    } catch (err) {
      showError(`Failed to load store comparison for "${gameTitle}".`);
    }
  }

  function renderDetails(data) {
    if (!data || !data.storeDeals || data.storeDeals.length === 0) {
      detailsContainer.innerHTML = `
        <div class="empty-state">
          <h3>No Store Deals Found</h3>
          <p>We could not find active storefront deals for "${escapeHtml(gameTitle)}".</p>
          <a href="index.html" class="btn btn-primary" style="margin-top: 1rem;">Back to Deals</a>
        </div>
      `;
      return;
    }

    const formattedCheapest = CurrencyEngine.formatPrice(data.cheapestPrice);
    const bestDeal = data.storeDeals.find(d => d.salePrice === data.cheapestPrice) || data.storeDeals[0];

    const tableRows = data.storeDeals.map(deal => {
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

    detailsContainer.innerHTML = `
      <section class="details-hero">
        <div class="details-hero__image">
          <img src="${escapeHtml(data.thumbnail || bestDeal.thumbnail)}" alt="${escapeHtml(data.title)}" onerror="this.src='https://via.placeholder.com/280x140?text=No+Image'">
        </div>
        <div class="details-hero__content">
          <div class="best-price-badge">
            🏷️ BEST PRICE: ${formattedCheapest} on ${escapeHtml(bestDeal.storeName)}
          </div>
          <h1 class="details-hero__title">${escapeHtml(data.title)}</h1>
          <p style="color: var(--text-muted); font-size: 0.95rem; margin-bottom: 1.5rem;">
            Comparing live prices across ${data.storeDeals.length} storefronts. Select a store below to purchase directly.
          </p>
          <button id="track-hero-btn" class="btn btn-accent" style="padding: 0.65rem 1.25rem;">
            Track Price Alert 🔔
          </button>
        </div>
      </section>

      <section class="matrix-section">
        <div class="section-header">
          <h2 class="section-title">Live Store Comparison Matrix</h2>
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

    document.getElementById('track-hero-btn').addEventListener('click', () => {
      openTrackModal(data.title, data.gameId, data.cheapestPrice);
    });
  }

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
        <span>Comparing prices across stores for "${escapeHtml(gameTitle)}"...</span>
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
    return str.replace(/[&<>"']/g, m => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    })[m]);
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
