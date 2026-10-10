/**
 * Main Application Logic for AWESOME™ Game Store (index.html)
 */
document.addEventListener('DOMContentLoaded', () => {
  const dealsGrid = document.getElementById('deals-grid');
  const searchInput = document.getElementById('search-input');
  const searchBtn = document.getElementById('search-btn');
  const storeSelect = document.getElementById('store-select');
  const quickTags = document.querySelectorAll('.quick-tag');

  // Modals & Track Price
  const trackModal = document.getElementById('track-modal');
  const closeTrackModal = document.getElementById('close-track-modal');
  const trackForm = document.getElementById('track-form');
  const trackGameTitle = document.getElementById('track-game-title');
  const trackGameId = document.getElementById('track-game-id');
  const trackGameNameDisplay = document.getElementById('track-game-name-display');
  const trackEmail = document.getElementById('track-email');
  const trackTargetPrice = document.getElementById('track-target-price');

  const myWishlistBtn = document.getElementById('my-wishlist-btn');
  const wishlistModal = document.getElementById('wishlist-modal');
  const closeWishlistModal = document.getElementById('close-wishlist-modal');
  const wishlistSearchEmail = document.getElementById('wishlist-search-email');
  const loadUserWishlistBtn = document.getElementById('load-user-wishlist-btn');
  const wishlistItemsContainer = document.getElementById('wishlist-items-container');

  let currentDeals = [];
  let debounceTimer = null;

  // Initialize Currency Selector
  CurrencyEngine.initSelector('currency-select');

  // Remember last entered email
  const savedEmail = localStorage.getItem('awesome_user_email') || '';
  if (savedEmail) {
    trackEmail.value = savedEmail;
    wishlistSearchEmail.value = savedEmail;
  }

  // Listen to currency updates and re-render current deals instantly
  window.addEventListener('currencyChange', () => {
    if (currentDeals.length > 0) {
      renderDeals(currentDeals);
    }
  });

  // Load Stores into Filter Dropdown
  async function loadStores() {
    try {
      const stores = await ApiService.getStores();
      stores.forEach(store => {
        const option = document.createElement('option');
        option.value = store.id;
        option.textContent = store.name;
        storeSelect.appendChild(option);
      });
    } catch (err) {
      console.error('Failed to load stores:', err);
    }
  }

  // Load Deals (Top Deals or Search Query)
  async function loadDeals() {
    const query = searchInput.value.trim();
    const storeId = storeSelect.value;

    const headingEl = document.getElementById('deals-grid-heading');
    const subtitleEl = document.getElementById('deals-grid-subtitle');
    const trendingBadge = document.getElementById('trending-badge');

    showLoading();

    try {
      if (query.length > 0) {
        if (headingEl) headingEl.textContent = `Search Results for "${query}"`;
        if (subtitleEl) subtitleEl.textContent = 'Showing 1 card per game with lowest price. In-game currency filtered out. Click any game to see all store options.';
        if (trendingBadge) trendingBadge.textContent = '🔍 Filtered Results';
        currentDeals = await ApiService.searchDeals(query, storeId);
      } else {
        if (headingEl) headingEl.textContent = '🔥 Real-Time Trending Games & Top Deals';
        if (subtitleEl) subtitleEl.textContent = 'Live global trends aggregated from Steam Storefront & digital stores. 1 card per game with the lowest available price.';
        if (trendingBadge) trendingBadge.textContent = '🔴 Live Real-Time Trends';
        currentDeals = await ApiService.getTopDeals(24);
        if (storeId) {
          currentDeals = currentDeals.filter(d => d.storeId === storeId);
        }
      }
      renderDeals(currentDeals);
    } catch (err) {
      showError('Failed to fetch deals. Please check backend connection.');
    }
  }

  // Render Deals Grid
  function renderDeals(deals) {
    if (!deals || deals.length === 0) {
      dealsGrid.innerHTML = `
        <div class="empty-state" style="grid-column: 1 / -1;">
          <h3>No Deals Found</h3>
          <p>Try searching for a different game title or selecting another store.</p>
        </div>
      `;
      return;
    }

    dealsGrid.innerHTML = deals.map(deal => {
      const formattedNormal = CurrencyEngine.formatPrice(deal.normalPrice);
      const formattedSale = CurrencyEngine.formatPrice(deal.salePrice);
      const savingsRounded = Math.round(deal.savingsPercent);
      const compareUrl = `details.html?title=${encodeURIComponent(deal.title)}&gameId=${encodeURIComponent(deal.gameId || '')}&steamAppId=${encodeURIComponent(deal.steamAppId || '')}`;

      return `
        <article class="deal-card">
          <a href="${compareUrl}" class="deal-card__thumb" title="Click to view details & comparison for ${escapeHtml(deal.title)}">
            <img src="${escapeHtml(deal.thumbnail)}" alt="${escapeHtml(deal.title)}" loading="lazy" onerror="this.src='https://via.placeholder.com/231x87?text=No+Image'">
            ${savingsRounded > 0 ? `<span class="deal-card__badge-discount">-${savingsRounded}% OFF</span>` : ''}
          </a>
          <div class="deal-card__body">
            <div class="deal-card__store">
              <span>Lowest on ${escapeHtml(deal.storeName)}</span>
            </div>
            <h3 class="deal-card__title" title="${escapeHtml(deal.title)}">
              <a href="${compareUrl}" class="deal-card__title-link" title="Click to view details & comparison for ${escapeHtml(deal.title)}">
                ${escapeHtml(deal.title)}
              </a>
            </h3>
            
            <div class="deal-card__price-row">
              ${deal.savingsPercent > 0 ? `<span class="deal-card__price-normal">${formattedNormal}</span>` : ''}
              <span class="deal-card__price-sale">${formattedSale}</span>
            </div>
            
            <div class="deal-card__actions">
              <a href="${deal.storeUrl || getDirectStoreUrl(deal.storeName, deal.storeId, deal.title, deal.steamAppId)}" target="_blank" rel="noopener noreferrer" class="btn btn-outline" style="font-size: 0.78rem; padding: 0.45rem 0.5rem;" title="Visit ${escapeHtml(deal.storeName)}">
                Visit Store ↗
              </a>
              <a href="${compareUrl}" class="btn btn-outline" style="font-size: 0.78rem; padding: 0.45rem 0.5rem;" title="Compare prices & view game information">
                Compare & Info
              </a>
              <button class="btn btn-accent track-btn" 
                      data-title="${escapeHtml(deal.title)}" 
                      data-gameid="${escapeHtml(deal.gameId)}" 
                      data-price="${deal.salePrice}" 
                      style="font-size: 0.78rem; padding: 0.45rem 0.5rem;">
                Track 🔔
              </button>
            </div>
          </div>
        </article>
      `;
    }).join('');

    // Attach Track Button Click Handlers
    document.querySelectorAll('.track-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const title = e.currentTarget.getAttribute('data-title');
        const gameId = e.currentTarget.getAttribute('data-gameid');
        const salePriceUsd = parseFloat(e.currentTarget.getAttribute('data-price')) || 0;

        openTrackModal(title, gameId, salePriceUsd);
      });
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

  // Submit Price Track Form
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
      alert(`Price alert saved! We will track "${title}" for price drops below ${CurrencyEngine.formatPrice(targetPrice / (CurrencyEngine.RATES[currency]?.rate || 1.0))}.`);
      trackModal.classList.remove('active');
    } catch (err) {
      alert(`Error setting alert: ${err.message}`);
    }
  });

  // Open Wishlist Modal
  myWishlistBtn.addEventListener('click', () => {
    wishlistModal.classList.add('active');
    if (wishlistSearchEmail.value.trim()) {
      loadUserWishlist();
    }
  });

  closeWishlistModal.addEventListener('click', () => {
    wishlistModal.classList.remove('active');
  });

  loadUserWishlistBtn.addEventListener('click', () => {
    loadUserWishlist();
  });

  async function loadUserWishlist() {
    const email = wishlistSearchEmail.value.trim();
    if (!email) {
      wishlistItemsContainer.innerHTML = '<p style="color: #ef4444; text-align: center;">Please enter a valid email.</p>';
      return;
    }

    localStorage.setItem('awesome_user_email', email);
    const curCurrency = CurrencyEngine.getSelectedCurrency();

    wishlistItemsContainer.innerHTML = '<div class="loading-spinner"><div class="spinner"></div><span>Loading tracked items...</span></div>';

    try {
      const items = await ApiService.getWishlist(email, curCurrency);

      if (!items || items.length === 0) {
        wishlistItemsContainer.innerHTML = `<div class="empty-state"><p>No price alerts found for "${escapeHtml(email)}".</p></div>`;
        return;
      }

      wishlistItemsContainer.innerHTML = `
        <div style="display: flex; flex-direction: column; gap: 0.75rem;">
          ${items.map(item => `
            <div style="background: #121212; border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 1rem; display: flex; align-items: center; justify-content: space-between; gap: 1rem;">
              <div>
                <h4 style="font-size: 1rem; margin-bottom: 0.25rem;">${escapeHtml(item.gameTitle)}</h4>
                <div style="font-size: 0.82rem; color: var(--text-muted);">
                  Target: <strong style="color: var(--text-primary);">${CurrencyEngine.formatPrice(item.targetPrice / (CurrencyEngine.RATES[curCurrency]?.rate || 1.0))}</strong> | 
                  Current: <strong style="color: var(--accent-green-bright);">${CurrencyEngine.formatPrice(item.currentBestPrice / (CurrencyEngine.RATES[curCurrency]?.rate || 1.0))}</strong> (${escapeHtml(item.bestStore)})
                </div>
              </div>
              <div style="display: flex; align-items: center; gap: 0.75rem;">
                <span class="deal-card__badge-discount" style="position: static; ${item.isTriggered ? 'background: var(--accent-green);' : 'background: #3f3f46; color: #fff;'}">
                  ${item.status}
                </span>
                <button class="btn btn-outline remove-wishlist-btn" data-id="${item.id}" style="padding: 0.35rem 0.6rem; font-size: 0.8rem; color: #ef4444; border-color: rgba(239, 68, 68, 0.3);">
                  ✕
                </button>
              </div>
            </div>
          `).join('')}
        </div>
      `;

      // Attach remove button click handlers
      document.querySelectorAll('.remove-wishlist-btn').forEach(btn => {
        btn.addEventListener('click', async (e) => {
          const id = e.currentTarget.getAttribute('data-id');
          if (confirm('Delete this price alert?')) {
            try {
              await ApiService.removeFromWishlist(id);
              loadUserWishlist();
            } catch (err) {
              alert(`Failed to delete alert: ${err.message}`);
            }
          }
        });
      });

    } catch (err) {
      wishlistItemsContainer.innerHTML = `<p style="color: #ef4444; text-align: center;">Error loading wishlist: ${escapeHtml(err.message)}</p>`;
    }
  }

  function showLoading() {
    dealsGrid.innerHTML = `
      <div class="loading-spinner" style="grid-column: 1 / -1;">
        <div class="spinner"></div>
        <span>Fetching live game deals...</span>
      </div>
    `;
  }

  function showError(msg) {
    dealsGrid.innerHTML = `
      <div class="empty-state" style="grid-column: 1 / -1; border-color: rgba(239, 68, 68, 0.4);">
        <h3 style="color: #ef4444;">Error Loading Data</h3>
        <p>${escapeHtml(msg)}</p>
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

  // Search Event Handlers
  searchInput.addEventListener('input', () => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      loadDeals();
    }, 350);
  });

  searchBtn.addEventListener('click', () => {
    clearTimeout(debounceTimer);
    loadDeals();
  });

  searchInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      clearTimeout(debounceTimer);
      loadDeals();
    }
  });

  storeSelect.addEventListener('change', () => {
    loadDeals();
  });

  quickTags.forEach(tag => {
    tag.addEventListener('click', () => {
      const gameTitle = tag.getAttribute('data-query');
      if (gameTitle) {
        searchInput.value = gameTitle;
        loadDeals();
      }
    });
  });

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

  // Init
  loadStores();
  loadDeals();
});
