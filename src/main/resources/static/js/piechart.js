// Renders a simple SVG pie chart + legend from data-chart-label/data-chart-value
// attributes on a page's statistics summary rows. No external chart library.
(function () {
  var PALETTE_LIGHT = ['#2a78d6', '#1baf7a', '#eda100', '#008300', '#4a3aa7', '#e34948', '#e87ba4', '#eb6834'];
  var PALETTE_DARK   = ['#3987e5', '#199e70', '#c98500', '#008300', '#9085e9', '#e66767', '#d55181', '#d95926'];
  var MAX_SLICES = 6; // + one "Sonstige" bucket for the tail = 7, within the 8-hue palette

  function isDarkMode() {
    return document.documentElement.getAttribute('data-bs-theme') === 'dark';
  }

  function esc(s) {
    return (s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  function buildData(rowSelector) {
    var data = [];
    document.querySelectorAll(rowSelector).forEach(function (row) {
      var label = row.getAttribute('data-chart-label');
      var value = parseFloat(row.getAttribute('data-chart-value'));
      if (label && !isNaN(value) && value > 0) data.push({ label: label, value: value });
    });
    data.sort(function (a, b) { return b.value - a.value; });
    if (data.length > MAX_SLICES + 1) {
      var head = data.slice(0, MAX_SLICES);
      var tail = data.slice(MAX_SLICES).reduce(function (sum, d) { return sum + d.value; }, 0);
      head.push({ label: window.wealthPieChartOtherLabel || 'Sonstige', value: tail });
      data = head;
    }
    return data;
  }

  function paint(container, rowSelector) {
    var data = buildData(rowSelector);
    if (data.length === 0) { container.style.display = 'none'; return; }

    var total = data.reduce(function (sum, d) { return sum + d.value; }, 0);
    var palette = isDarkMode() ? PALETTE_DARK : PALETTE_LIGHT;
    var surface = isDarkMode() ? '#1a1a19' : '#fcfcfb';
    var cx = 100, cy = 100, r = 90;

    var slices = [];
    var legend = [];
    var angle = -Math.PI / 2;
    data.forEach(function (d, i) {
      var frac = d.value / total;
      var color = palette[i % palette.length];
      var next = angle + frac * 2 * Math.PI;
      var path;
      if (frac >= 0.9999) {
        path = '<circle cx="' + cx + '" cy="' + cy + '" r="' + r + '" fill="' + color + '"></circle>';
      } else {
        var x1 = (cx + r * Math.cos(angle)).toFixed(2), y1 = (cy + r * Math.sin(angle)).toFixed(2);
        var x2 = (cx + r * Math.cos(next)).toFixed(2), y2 = (cy + r * Math.sin(next)).toFixed(2);
        var largeArc = frac > 0.5 ? 1 : 0;
        path = '<path d="M ' + cx + ' ' + cy + ' L ' + x1 + ' ' + y1 +
          ' A ' + r + ' ' + r + ' 0 ' + largeArc + ' 1 ' + x2 + ' ' + y2 + ' Z" ' +
          'fill="' + color + '" stroke="' + surface + '" stroke-width="2"></path>';
      }
      slices.push('<g>' + path + '<title>' + esc(d.label) + ': ' + (frac * 100).toFixed(1) + ' %</title></g>');
      legend.push(
        '<div class="d-flex align-items-center gap-2">' +
        '<span style="display:inline-block;width:10px;height:10px;border-radius:2px;background:' + color + ';flex:none"></span>' +
        '<span class="text-truncate">' + esc(d.label) + '</span>' +
        '<span class="text-muted ms-auto" style="white-space:nowrap">' + (frac * 100).toFixed(1) + ' %</span>' +
        '</div>'
      );
      angle = next;
    });

    container.innerHTML =
      '<div class="d-flex flex-wrap align-items-center gap-4 mb-3">' +
      '<svg viewBox="0 0 200 200" width="180" height="180" role="img" style="flex:none">' + slices.join('') + '</svg>' +
      '<div class="d-flex flex-column gap-1" style="min-width:200px;max-width:320px">' + legend.join('') + '</div>' +
      '</div>';
  }

  // Re-paints in place when the user toggles light/dark mode (which only flips the
  // data-bs-theme attribute, no page reload), so the chart's baked-in colors stay in sync.
  window.wealthRenderPieChart = function (container, rowSelector) {
    if (!container) return;
    paint(container, rowSelector);
    if (container.style.display === 'none') return;
    new MutationObserver(function () { paint(container, rowSelector); })
      .observe(document.documentElement, { attributes: true, attributeFilter: ['data-bs-theme'] });
  };
})();
