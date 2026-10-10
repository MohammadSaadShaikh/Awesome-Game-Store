/**
 * API Service Client for AWESOME™ Game Store Backend
 */
const ApiService = (() => {
  const BASE_URL = '/api/v1';

  async function fetchJson(endpoint, options = {}) {
    try {
      const response = await fetch(`${BASE_URL}${endpoint}`, options);
      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || `HTTP error ${response.status}`);
      }
      if (response.status === 24 || response.status === 204) {
        return null;
      }
      return await response.json();
    } catch (error) {
      console.error(`API Fetch Error [${endpoint}]:`, error);
      throw error;
    }
  }

  async function getStores() {
    return await fetchJson('/stores');
  }

  async function getTopDeals(limit = 20, sortBy = 'Deal Rating') {
    return await fetchJson(`/deals?limit=${limit}&sortBy=${encodeURIComponent(sortBy)}`);
  }

  async function searchDeals(query, storeId = '', maxPrice = '') {
    let url = `/deals/search?query=${encodeURIComponent(query)}`;
    if (storeId) url += `&storeId=${encodeURIComponent(storeId)}`;
    if (maxPrice) url += `&maxPrice=${encodeURIComponent(maxPrice)}`;
    return await fetchJson(url);
  }

  async function compareDeals(title, gameId = '', steamAppId = '') {
    let url = `/deals/compare?title=${encodeURIComponent(title)}`;
    if (gameId) url += `&gameId=${encodeURIComponent(gameId)}`;
    if (steamAppId) url += `&steamAppId=${encodeURIComponent(steamAppId)}`;
    return await fetchJson(url);
  }

  async function getGameInfo(title, gameId = '', steamAppId = '') {
    let url = `/deals/info?title=${encodeURIComponent(title)}`;
    if (gameId) url += `&gameId=${encodeURIComponent(gameId)}`;
    if (steamAppId) url += `&steamAppId=${encodeURIComponent(steamAppId)}`;
    return await fetchJson(url);
  }

  async function addToWishlist(requestPayload) {
    return await fetchJson('/wishlist', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(requestPayload)
    });
  }

  async function getWishlist(email, currency = 'USD') {
    return await fetchJson(`/wishlist?email=${encodeURIComponent(email)}&currency=${encodeURIComponent(currency)}`);
  }

  async function removeFromWishlist(id) {
    return await fetchJson(`/wishlist/${id}`, {
      method: 'DELETE'
    });
  }

  async function triggerWishlistSync() {
    return await fetchJson('/wishlist/sync-now', {
      method: 'POST'
    });
  }

  return {
    getStores,
    getTopDeals,
    searchDeals,
    compareDeals,
    getGameInfo,
    addToWishlist,
    getWishlist,
    removeFromWishlist,
    triggerWishlistSync
  };
})();
