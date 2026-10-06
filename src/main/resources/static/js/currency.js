/**
 * Currency Conversion Engine for AWESOME™ Game Store
 */
const CurrencyEngine = (() => {
  const STORAGE_KEY = 'awesome_selected_currency';

  const RATES = {
    USD: { rate: 1.0, locale: 'en-US', symbol: '$', name: 'USD ($)' },
    EUR: { rate: 0.92, locale: 'de-DE', symbol: '€', name: 'EUR (€)' },
    GBP: { rate: 0.79, locale: 'en-GB', symbol: '£', name: 'GBP (£)' },
    INR: { rate: 83.5, locale: 'en-IN', symbol: '₹', name: 'INR (₹)' },
    CAD: { rate: 1.36, locale: 'en-CA', symbol: 'CA$', name: 'CAD (CA$)' },
    AUD: { rate: 1.52, locale: 'en-AU', symbol: 'A$', name: 'AUD (A$)' }
  };

  let currentCurrency = localStorage.getItem(STORAGE_KEY) || 'USD';
  if (!RATES[currentCurrency]) {
    currentCurrency = 'USD';
  }

  function getSelectedCurrency() {
    return currentCurrency;
  }

  function setCurrency(currencyCode) {
    if (RATES[currencyCode]) {
      currentCurrency = currencyCode;
      localStorage.setItem(STORAGE_KEY, currencyCode);
      window.dispatchEvent(new CustomEvent('currencyChange', { detail: { currency: currencyCode } }));
    }
  }

  function formatPrice(amountInUsd) {
    if (amountInUsd === null || amountInUsd === undefined) {
      return '$0.00';
    }
    const config = RATES[currentCurrency] || RATES.USD;
    const convertedAmount = amountInUsd * config.rate;

    try {
      return new Intl.NumberFormat(config.locale, {
        style: 'currency',
        currency: currentCurrency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      }).format(convertedAmount);
    } catch (e) {
      return `${config.symbol}${convertedAmount.toFixed(2)}`;
    }
  }

  function initSelector(selectElementId = 'currency-select') {
    const select = document.getElementById(selectElementId);
    if (!select) return;

    select.innerHTML = '';
    Object.keys(RATES).forEach(code => {
      const option = document.createElement('option');
      option.value = code;
      option.textContent = RATES[code].name;
      if (code === currentCurrency) {
        option.selected = true;
      }
      select.appendChild(option);
    });

    select.addEventListener('change', (e) => {
      setCurrency(e.target.value);
    });
  }

  return {
    getSelectedCurrency,
    setCurrency,
    formatPrice,
    initSelector,
    RATES
  };
})();
