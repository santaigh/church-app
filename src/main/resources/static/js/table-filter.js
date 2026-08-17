/*
 * Adds a search box under every column heading of a table marked data-filterable.
 *
 * Written once and applied to every table, so a new module gets column search by adding
 * one attribute rather than by copying a page's worth of markup.
 *
 * Filtering is done in the browser over the rows already rendered. That is honest while a
 * parish list fits on one page -- a few hundred rows at most. The day these lists are
 * paginated, this has to move to the server, or the boxes will quietly search only the
 * page you happen to be looking at.
 */
document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('table[data-filterable]').forEach(setUpTable);
});

function setUpTable(table) {
    var headRow = table.tHead && table.tHead.rows[0];
    var body = table.tBodies[0];
    if (!headRow || !body || body.rows.length === 0) {
        return;
    }

    var filterRow = document.createElement('tr');
    filterRow.className = 'filter-row';

    Array.prototype.forEach.call(headRow.cells, function (cell, index) {
        var holder = document.createElement('th');

        // Action columns carry data-no-filter: they hold buttons, not data to search.
        if (cell.textContent.trim() !== '' && !cell.hasAttribute('data-no-filter')) {
            var input = document.createElement('input');
            input.type = 'search';
            input.className = 'filter-input';
            input.setAttribute('aria-label', 'Search ' + cell.textContent.trim());
            input.placeholder = cell.textContent.trim();
            input.addEventListener('input', function () {
                applyFilters(table, body, filterRow);
            });
            holder.appendChild(input);
        }

        filterRow.appendChild(holder);
    });

    table.tHead.appendChild(filterRow);
    ensureCounter(table);
}

function applyFilters(table, body, filterRow) {
    var terms = Array.prototype.map.call(filterRow.cells, function (cell) {
        var input = cell.querySelector('input');
        return input ? input.value.trim().toLowerCase() : '';
    });

    var shown = 0;
    Array.prototype.forEach.call(body.rows, function (row) {
        var matches = terms.every(function (term, index) {
            if (term === '') {
                return true;
            }
            var cell = row.cells[index];
            return cell && cell.textContent.toLowerCase().indexOf(term) !== -1;
        });
        row.hidden = !matches;
        if (matches) {
            shown += 1;
        }
    });

    updateCounter(table, shown, body.rows.length);
}

function ensureCounter(table) {
    var counter = document.createElement('p');
    counter.className = 'filter-count';
    counter.hidden = true;
    table.parentNode.insertBefore(counter, table.nextSibling);
    table.filterCounter = counter;
}

function updateCounter(table, shown, total) {
    var counter = table.filterCounter;
    if (!counter) {
        return;
    }
    if (shown === total) {
        counter.hidden = true;
        return;
    }
    counter.hidden = false;
    counter.textContent = shown === 0
        ? 'Nothing matches those filters.'
        : 'Showing ' + shown + ' of ' + total + '.';
}
