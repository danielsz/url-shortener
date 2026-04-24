/* global Chart */

const PLATFORMS = {
    'twitter':            { label: 'Twitter/X',      hex: '#378ADD' },
    'twitter-aggregator': { label: 'TW Aggregator',  hex: '#5B9BD5' },
    'bluesky':            { label: 'Bluesky',         hex: '#0085FF' },
    'mastodon':           { label: 'Mastodon',        hex: '#6364FF' },
    'facebook':           { label: 'Facebook',        hex: '#1D9E75' },
    'instagram':          { label: 'Instagram',       hex: '#D85A30' },
    'linkedin':           { label: 'LinkedIn',        hex: '#BA7517' },
    'pinterest':          { label: 'Pinterest',       hex: '#9B4F96' },
    'google':             { label: 'Google',          hex: '#E8A020' },
    'reddit':             { label: 'Reddit',          hex: '#FF4500' },
    'youtube':            { label: 'YouTube',         hex: '#FF0000' },
    'email':              { label: 'Email',           hex: '#4A90A4' },
    'direct':             { label: 'Direct',          hex: '#7a7970' },
    'other':              { label: 'Other',           hex: '#888780' },
    'all':                { label: 'All',             hex: '#4A8FC2' },  // --blue
};

const isDark    = matchMedia('(prefers-color-scheme: dark)').matches;
const gridColor = isDark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)';
const tickColor = isDark ? 'rgba(200,200,200,0.45)' : 'rgba(60,60,60,0.45)';

// ---------------------------------------------------------------------------
// Signal accumulator — merges RFC 7386 patches into full local state
// ---------------------------------------------------------------------------

let currentSignals = {
    daily:     {},
    platforms: {},
    countries: {},
    links:     [],
    confirmed: null,
};

function mergeDeep(target, patch) {
    if (!patch || typeof patch !== 'object' || Array.isArray(patch)) return patch;
    const result = Object.assign({}, target);
    for (const [key, val] of Object.entries(patch)) {
        result[key] = (val && typeof val === 'object' && !Array.isArray(val))
            ? mergeDeep(target[key] || {}, val)
            : val;
    }
    return result;
}

// ---------------------------------------------------------------------------
// Chart
// ---------------------------------------------------------------------------

function buildDatasets(daily) {
    const allDates = [...new Set(
        Object.values(daily).flatMap(d => Object.keys(d))
    )].sort();
    const datasets = Object.entries(daily).map(([key, data], i) => {
        const p = PLATFORMS[key] || { label: key, hex: '#888780' };
        return {
            label:           p.label,
            data:            allDates.map(d => data[d] || 0),
            borderColor:     p.hex,
            backgroundColor: p.hex + '33',
            fill:            i === 0 ? 'origin' : String(i - 1),
            borderWidth:     1,
            pointRadius:     0,
            tension:         0.35,
        };
    });
    return { allDates, datasets };
}

function initChart(daily) {
    const el = document.getElementById('tsChart');
    if (!el || el._chart) return;
    const { allDates, datasets } = buildDatasets(daily);
    el._chart = new Chart(el.getContext('2d'), {
        type: 'line',
        data: { labels: allDates, datasets },
        options: {
            responsive:          true,
            maintainAspectRatio: false,
            resizeDelay:         500,
            interaction:         { mode: 'index', intersect: false },
            plugins:             { legend: { display: false } },
            scales: {
                x: {
                    grid:   { color: gridColor },
                    ticks:  { color: tickColor, font: { family: 'monospace', size: 9 }, maxTicksLimit: 6 },
                    border: { display: false },
                },
                y: {
                    grid:   { color: gridColor },
                    ticks:  { color: tickColor, font: { family: 'monospace', size: 9 }, maxTicksLimit: 4 },
                    border: { display: false },
                },
            },
        },
    });
}

function updateChart(daily) {
    const el = document.getElementById('tsChart');
    if (!el) return;
    if (!el._chart) { initChart(daily); return; }
    const { allDates, datasets } = buildDatasets(daily);
    el._chart.data.labels   = allDates;
    el._chart.data.datasets = datasets;
    el._chart.update('none');
}

// ---------------------------------------------------------------------------
// Links
// ---------------------------------------------------------------------------

function renderLinks(links) {
    const el = document.getElementById('links-panel');
    if (!el || !links) return;
    if (!links.length) {
        el.innerHTML = '<span class="bar-label">No links yet</span>';
        return;
    }
    const max = links[0].clicks;
    el.innerHTML = links.map(l => `
        <a href="/admin/v2/link/${l.path}" style="text-decoration:none;color:inherit;display:block;">
            <div style="display:grid;grid-template-columns:minmax(0,1fr) 1fr 58px;gap:var(--space-xs);align-items:center;">
                <div class="bar-label" title="${l.url}">${l.desc || l.url}</div>
                <div class="bar-track-4">
                    <div class="bar-fill" style="width:${max ? (l.clicks / max * 100).toFixed(1) : 0}%;background:var(--text-t);opacity:0.35;"></div>
                </div>
                <div class="bar-count">${l.clicks.toLocaleString()}</div>
            </div>
        </a>`).join('');
}

// ---------------------------------------------------------------------------
// Countries
// ---------------------------------------------------------------------------

function renderCountries(countries) {
    const el = document.getElementById('countries-panel');
    if (!el || !countries) return;
    const entries = Object.entries(countries).sort((a, b) => b[1] - a[1]).slice(0, 10);
    if (!entries.length) {
        el.innerHTML = '<span class="bar-label">No data yet</span>';
        return;
    }
    const max = entries[0][1];
    el.innerHTML = entries.map(([code, n]) => `
        <div class="country-row spread">
            <span>${code}</span>
            <div class="country-bar">
                <div class="country-bar-fill" style="width:${(n / max * 100).toFixed(1)}%;"></div>
            </div>
            <span class="country-count">${n}</span>
        </div>`).join('');
}

// ---------------------------------------------------------------------------
// Platforms
// ---------------------------------------------------------------------------

function renderPlatforms(platforms) {
    const el = document.getElementById('platforms-panel');
    if (!el || !platforms) return;
    const entries = Object.entries(platforms).sort((a, b) => b[1] - a[1]);
    if (!entries.length) {
        el.innerHTML = '<span class="bar-label">No data yet</span>';
        return;
    }
    const max = entries[0][1];
    el.innerHTML = entries.map(([key, n]) => {
        const p = PLATFORMS[key] || PLATFORMS.other;
        return `
            <div>
                <div class="ref-label-row">
                    <div class="ref-source cluster cluster--tight">
                        <div class="src-dot" style="background:${p.hex};"></div>
                        ${p.label}
                    </div>
                    <span class="bar-count">${n.toLocaleString()}</span>
                </div>
                <div class="bar-track">
                    <div class="bar-fill" style="width:${(n / max * 100).toFixed(1)}%;background:${p.hex};opacity:0.7;"></div>
                </div>
            </div>`;
    }).join('');
}

// ---------------------------------------------------------------------------
// Feed
// ---------------------------------------------------------------------------


function renderFeed(feed) {
    const el = document.getElementById('feed-panel');
    if (!el || !feed) return;
    el.innerHTML = feed.map((item, i) => `
        <div class="feed-row stack stack--tight" style="opacity:${Math.max(0.3, 1 - i * 0.15)};">
            <div class="feed-row__top spread spread--baseline">
                <div class="feed-short">${item.short}</div>
                <time class="feed-time">${item.time}</time>
            </div>
            <div class="feed-url">→ ${item.url}</div>
        </div>`).join('');
}

// ---------------------------------------------------------------------------
// Confirmed
// ---------------------------------------------------------------------------

function renderConfirmed(confirmed, platforms) {
    const el = document.getElementById('confirmed-panel');
    if (!el || !confirmed) return;
    el.innerHTML = Object.entries(confirmed).map(([key, confirmedN]) => {
        const p     = PLATFORMS[key] || PLATFORMS.other;
        const total = (platforms && platforms[key]) || confirmedN;
        const rate  = (confirmedN / total * 100).toFixed(1);
        return `
            <div class="confirmed-row spread">
                <div class="ref-source cluster cluster--tight">
                    <div class="src-dot" style="background:${p.hex};"></div>
                    ${p.label}
                </div>
                <span class="confirmed-rate">${rate}%</span>
                <span class="bar-count">${confirmedN} / ${total}</span>
            </div>`;
    }).join('');
}

// ---------------------------------------------------------------------------
// Signal patch listener
// ---------------------------------------------------------------------------

document.addEventListener('datastar-signal-patch', (e) => {
    const patch = e.detail || {};
    currentSignals = mergeDeep(currentSignals, patch);
    const sig = currentSignals;
    if (patch.daily)                   updateChart(sig.daily);
    if (patch.links)                   renderLinks(sig.links);
    if (patch.countries)               renderCountries(sig.countries);
    if (patch.platforms)               renderPlatforms(sig.platforms);
    if (patch.feed)                    renderFeed(sig.feed);
    if (patch.confirmed !== undefined) renderConfirmed(sig.confirmed, sig.platforms);
});

// ---------------------------------------------------------------------------
// bfcache
// ---------------------------------------------------------------------------

window.addEventListener('pageshow', (e) => {
    // console.log('pageshow fired', { persisted: e.persisted, time: new Date().toISOString() });
    if (e.persisted) {
        console.log('bfcache restore detected — reloading');
        window.location.reload();
    }
});

