'use strict';

/* ═══════════════════════════════════════════
   CONSTANTS
   ═══════════════════════════════════════════ */

const TYPE_COLOR = {
  Normal:   '#9BA0A8',
  Fire:     '#FF7035',
  Water:    '#4a82FF',
  Grass:    '#5ec454',
  Electric: '#f5c518',
  Psychic:  '#f04888',
  Dark:     '#8060a0',
  Rock:     '#976119',
  Fighting: '#d86a25'
};

const POKEMON_EMOJI = {
  Emberfox:  '🦊',
  Tidalfin:  '🐬',
  Thornback: '🦎',
  Zapwing:   '🦅',
  Mindweave: '🔮',
  Grimclaw:  '🐺',
  Thedude:   '⭐',
  Rockruff:  '🪨',
  Elmacho:   '👊',
};

const STATUS_COLOR = {
  BURN:      '#FF7035',
  PARALYSIS: '#f5c518',
  SLEEP:     '#8060a0',
  POISON:    '#a040c0',
};

const LOG_DELAY_MS = 600;

/* ═══════════════════════════════════════════
   STATE
   ═══════════════════════════════════════════ */

let allPokemon      = [];
let team            = [null, null, null];  // { pid } per slot
let teamMoves       = [[], [], []];        // selected move ids per slot
let currentSlot     = 0;                   // which slot we are picking for
let selectedPkmnId  = null;
let selectedMoveIds = new Set();
let busy            = false;
let lastLogLen      = 0;

/* ═══════════════════════════════════════════
   UTILITY
   ═══════════════════════════════════════════ */

function api(method, path, body) {
  // JavaFX WebView's bundled WebKit engine does not reliably support
  // fetch(), which silently breaks async click handlers. XMLHttpRequest
  // works consistently across WebView versions, so we use it here.
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open(method, path, true);
    xhr.setRequestHeader('Content-Type', 'application/json');
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try { resolve(JSON.parse(xhr.responseText)); }
        catch (e) { reject(e); }
      } else {
        reject(new Error(`Request failed: ${xhr.status} ${path}`));
      }
    };
    xhr.onerror = () => reject(new Error(`Network error: ${path}`));
    xhr.send(body !== undefined ? JSON.stringify(body) : null);
  });
}

function showScreen(id) {
  document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
  document.getElementById(id).classList.add('active');
}

function sprite(name) { return POKEMON_EMOJI[name] || '❓'; }
function typeColor(t) { return TYPE_COLOR[t] || '#888'; }

function typeBadge(typeName) {
  const col = typeColor(typeName);
  return `<span class="type-badge" style="color:${col};background:${col}33;border:1px solid ${col}55">${typeName}</span>`;
}

function statusBadge(status) {
  if (!status || status === 'NONE') return '';
  const col = STATUS_COLOR[status] || '#888';
  return `<span class="status-badge-inner" style="color:${col};background:${col}22;border:1px solid ${col}55">${status}</span>`;
}

function hpColor(hp, max) {
  const p = hp / max;
  if (p > 0.5)  return '#4cf07a';
  if (p > 0.25) return '#f5c518';
  return '#f04c4c';
}

function setHpBar(fillId, textId, hp, max) {
  const fill = document.getElementById(fillId);
  const text = document.getElementById(textId);
  const pct  = Math.max(0, hp / max * 100);
  fill.style.width      = pct + '%';
  fill.style.background = hpColor(hp, max);
  text.textContent      = `${hp} / ${max}`;
}

function setButtonsEnabled(enabled) {
  document.querySelectorAll('.move-btn').forEach(b => b.disabled = !enabled);
}

/* ═══════════════════════════════════════════
   LOG — ANIMATED LINE BY LINE
   ═══════════════════════════════════════════ */

const LOG_PATTERNS = [
  { match: /^\[START\]/,              cls: 'start'  },
  { match: /^\[TURN\]|\[SPEED\]/,     cls: 'coin'   },
  { match: /^\[KO\]/,                 cls: 'ko'     },
  { match: /^\[MISS\]|\[Miss\]/,       cls: 'miss'   },
  { match: /^\[IMMUNE\]|\[Immune\]/,   cls: 'noop'   },
  { match: /^\[NO PP\]|\[No PP\]/,     cls: 'noop'   },
  { match: /^\[SKIP\]/,               cls: 'noop'   },
  { match: /^\[STATUS\]/,             cls: 'status' },
  { match: /^\[PAR\]/,                cls: 'noop'   },
  { match: /^\[SLP\]/,                cls: 'noop'   },
  { match: /^\[WAKE\]/,               cls: 'start'  },
  { match: /^\[PSN\]/,                cls: 'poison' },
  { match: /^\[BRN\]/,                cls: 'burn'   },
  { match: /^\[SWITCH\]/,             cls: 'switch' },
  { match: /SUPER EFFECTIVE/,         cls: 'super'  },
  { match: /not very effective/,      cls: 'noteff' },
  { match: /\[STAB\]/,                cls: 'stab'   },
  { match: /^\[ATK\]/,                cls: 'atk'    },
];

function classifyLine(line) {
  for (const p of LOG_PATTERNS) {
    if (p.match.test(line)) return p.cls;
  }
  return '';
}

function clearLog() {
  document.getElementById('battle-log').innerHTML = '';
  lastLogLen = 0;
}

function appendLog(rawLog, onDone) {
  const logDiv   = document.getElementById('battle-log');
  const lines    = rawLog.split('\n').filter(l => l.trim());
  const newLines = lines.slice(lastLogLen);
  lastLogLen     = lines.length;

  if (newLines.length === 0) { if (onDone) onDone(); return; }

  let i = 0;
  function showNext() {
    if (i >= newLines.length) { if (onDone) onDone(); return; }
    const line = newLines[i++];
    const span = document.createElement('span');
    span.className   = 'log-line ' + classifyLine(line);
    span.textContent = line;
    logDiv.appendChild(span);
    logDiv.appendChild(document.createElement('br'));
    logDiv.scrollTop = logDiv.scrollHeight;
    setTimeout(showNext, LOG_DELAY_MS);
  }
  showNext();
}

/* ═══════════════════════════════════════════
   SCREEN 1 — WELCOME
   ═══════════════════════════════════════════ */

document.getElementById('btn-new-game').addEventListener('click', async () => {
  allPokemon      = await api('GET', '/api/pokemon');
  team            = [null, null, null];
  teamMoves       = [[], [], []];
  currentSlot     = 0;
  selectedPkmnId  = null;
  selectedMoveIds = new Set();
  renderPokemonSelect();
  showScreen('screen-pokemon');
});

/* ═══════════════════════════════════════════
   SCREEN 2 — POKEMON SELECT
   ═══════════════════════════════════════════ */

function renderPokemonSelect() {
  document.getElementById('team-slot-label').textContent = `Pick Pokemon ${currentSlot + 1} of 3`;

  // team preview circles
  const preview    = document.getElementById('team-preview');
  const pickedIds  = team.filter(Boolean).map(t => t.pid);
  preview.innerHTML = team.map((t, i) => {
    const filled = t !== null;
    const name   = filled ? allPokemon[t.pid].name : '';
    return `<span class="team-preview-slot ${filled ? 'filled' : ''}">${filled ? sprite(name) : (i === currentSlot ? '▸' : '?')}</span>`;
  }).join('');

  const grid = document.getElementById('pokemon-grid');
  grid.innerHTML = '';

  allPokemon.forEach(p => {
    const alreadyPicked = pickedIds.includes(p.id);
    const card = document.createElement('div');
    card.className  = 'pokemon-card' + (alreadyPicked ? ' picked' : '');
    card.dataset.id = p.id;

    const col = typeColor(p.type);
    card.style.borderColor = col + '44';
    card.style.boxShadow   = `inset 0 0 30px ${col}0a`;

    card.innerHTML = `
      <div class="pcard-sprite">${sprite(p.name)}</div>
      <div class="pcard-name">${p.name}</div>
      <div class="pcard-type">${typeBadge(p.type)}</div>
      <div class="pcard-stats">
        <span>HP</span>  <span class="pcard-stat-val">${p.hp}</span>
        <span>ATK</span> <span class="pcard-stat-val">${p.attack}</span>
        <span>DEF</span> <span class="pcard-stat-val">${p.defence}</span>
        <span>SPD</span> <span class="pcard-stat-val">${p.speed}</span>
      </div>`;

    if (!alreadyPicked) card.addEventListener('click', () => selectPokemon(p.id));
    grid.appendChild(card);
  });
}

function selectPokemon(id) {
  selectedPkmnId  = id;
  selectedMoveIds = new Set();
  renderMoveSelect();
  showScreen('screen-moves');
}

/* ═══════════════════════════════════════════
   SCREEN 3 — MOVE SELECT
   ═══════════════════════════════════════════ */

function renderMoveSelect() {
  const pkm = allPokemon[selectedPkmnId];

  document.getElementById('move-select-pokemon-info').innerHTML = `
    <span class="ms-sprite">${sprite(pkm.name)}</span>
    <div>
      <div class="ms-name">${pkm.name}</div>
      <div class="ms-stats">
        HP ${pkm.hp} &nbsp; ATK ${pkm.attack} &nbsp; DEF ${pkm.defence} &nbsp; SPD ${pkm.speed}
        &nbsp; ${typeBadge(pkm.type)}
      </div>
    </div>`;

  const grid = document.getElementById('moves-grid');
  grid.innerHTML = '';

  pkm.moves.forEach(m => {
    const card    = document.createElement('div');
    card.className  = 'move-card';
    card.dataset.id = m.id;

    const mc       = typeColor(m.type);
    const catColor = m.category === 'physical' ? '#FF7035' : '#4a82FF';

    card.innerHTML = `
      <div class="move-card-name">${m.name}</div>
      <div class="move-card-stats">
        <span>${typeBadge(m.type)}</span>
        <span class="move-cat" style="color:${catColor}">${m.category}</span>
        <span>PWR <b style="color:${mc}">${m.power}</b> &nbsp; ACC <b>${m.accuracy}%</b></span>
      </div>`;

    card.addEventListener('click', () => toggleMove(m.id, card));
    grid.appendChild(card);
  });

  updateMoveCount();
}

function toggleMove(id, card) {
  if (selectedMoveIds.has(id)) {
    selectedMoveIds.delete(id);
    card.classList.remove('selected');
  } else if (selectedMoveIds.size < 4) {
    selectedMoveIds.add(id);
    card.classList.add('selected');
  }
  updateMoveCount();
}

function updateMoveCount() {
  const n   = selectedMoveIds.size;
  const btn = document.getElementById('btn-confirm-moves');
  document.getElementById('move-count-label').textContent = `${n} / 4 selected`;
  btn.disabled = (n !== 4);
}

document.getElementById('btn-confirm-moves').addEventListener('click', async () => {
  if (selectedMoveIds.size !== 4) return;

  // save this slot
  team[currentSlot]      = { pid: selectedPkmnId };
  teamMoves[currentSlot] = [...selectedMoveIds];
  currentSlot++;

  if (currentSlot < 3) {
    // still more pokemon to pick
    renderPokemonSelect();
    showScreen('screen-pokemon');
  } else {
    // all 3 picked — send to server and start battle
    const body = {
      pid0: team[0].pid,  moves0: teamMoves[0],
      pid1: team[1].pid,  moves1: teamMoves[1],
      pid2: team[2].pid,  moves2: teamMoves[2],
    };
    await api('POST', '/api/start', body);
    const state = await api('GET', '/api/state');
    clearLog();
    showScreen('screen-battle');
    initBattle(state);
  }
});

/* ═══════════════════════════════════════════
   SCREEN 4 — BATTLE
   ═══════════════════════════════════════════ */

function initBattle(state) {
  const pActive = state.player.slots[state.player.active];
  const eActive = state.enemy.slots[state.enemy.active];

  updateFighterUI('player', pActive);
  updateFighterUI('enemy',  eActive);
  renderMoveButtons(pActive.moves, false);
  renderPartySlots(state.player);
  renderEnemyParty(state.enemy);

  appendLog(state.log, () => {
    if (state.forceSwitch) showForceSwitchUI();
    else renderMoveButtons(pActive.moves, true);
  });
}

function updateFighterUI(side, f) {
  document.getElementById(`${side}-name`).textContent   = f.name;
  document.getElementById(`${side}-sprite`).textContent = sprite(f.name);
  document.getElementById(`${side}-atk`).textContent    = f.attack;
  document.getElementById(`${side}-def`).textContent    = f.defence;
  document.getElementById(`${side}-spd`).textContent    = f.speed;

  const badge = document.getElementById(`${side}-type-badge`);
  const col   = typeColor(f.type);
  badge.textContent       = f.type;
  badge.style.color       = col;
  badge.style.background  = col + '22';
  badge.style.borderColor = col + '55';

  document.getElementById(`${side}-status-badge`).innerHTML = statusBadge(f.status);

  setHpBar(`${side}-hp-fill`, `${side}-hp-text`, f.hp, f.maxHp);
}

function renderMoveButtons(moves, enabled) {
  moves.forEach((m, i) => {
    const btn      = document.getElementById(`mbtn-${i}`);
    const col      = typeColor(m.type);
    const ppPct    = m.pp / m.maxPp;
    const ppColor  = ppPct > 0.5 ? '#4cf07a' : ppPct > 0.2 ? '#f5c518' : '#f04c4c';
    const catColor = m.category === 'physical' ? '#FF7035' : m.category === 'special' ? '#4a82FF' : '#888';

    btn.disabled          = !enabled || m.pp <= 0;
    btn.style.borderColor = col + '88';
    btn.innerHTML = `
      <span class="mbtn-name">${m.name}</span>
      <span class="mbtn-meta">
        <span style="color:${col}">${m.type}</span>
        <span style="color:${catColor}"> ${m.category}</span>
        &nbsp; PWR:${m.power} ACC:${m.accuracy}%
        <span class="mbtn-pp" style="color:${ppColor}">PP ${m.pp}/${m.maxPp}</span>
      </span>`;
  });
}

function renderPartySlots(party) {
  const container = document.getElementById('player-party-slots');
  container.innerHTML = '';
  party.slots.forEach((f, i) => {
    const btn = document.createElement('button');
    btn.className = 'party-slot-btn'
      + (i === party.active ? ' active-slot'  : '')
      + (f.hp <= 0          ? ' fainted-slot' : '');

    btn.innerHTML = `
      <span class="pslot-sprite">${sprite(f.name)}</span>
      <span class="pslot-name">${f.name}</span>
      <div class="pslot-hp-track">
        <div class="pslot-hp-fill" style="width:${Math.max(0, f.hp/f.maxHp*100)}%;background:${hpColor(f.hp,f.maxHp)}"></div>
      </div>
      <span class="pslot-hp-text">${f.hp}/${f.maxHp}</span>`;

    if (i !== party.active && f.hp > 0) {
      btn.addEventListener('click', () => switchPokemon(i));
    } else {
      btn.disabled = true;
    }
    container.appendChild(btn);
  });
}

function renderEnemyParty(party) {
  document.getElementById('enemy-party-bar').innerHTML = party.slots.map((f, i) =>
    `<span class="enemy-party-dot ${f.hp <= 0 ? 'fainted' : ''} ${i === party.active ? 'active' : ''}" title="${f.name}">●</span>`
  ).join('');
}

function showForceSwitchUI() {
  setButtonsEnabled(false);
  document.querySelectorAll('.party-slot-btn:not(:disabled)').forEach(btn => {
    btn.classList.add('force-switch');
  });
  const logDiv = document.getElementById('battle-log');
  const span   = document.createElement('span');
  span.className   = 'log-line start';
  span.textContent = '▸ Choose your next Pokemon!';
  logDiv.appendChild(span);
  logDiv.appendChild(document.createElement('br'));
  logDiv.scrollTop = logDiv.scrollHeight;
}

async function switchPokemon(slot) {
  if (busy) return;
  busy = true;
  setButtonsEnabled(false);
  document.querySelectorAll('.party-slot-btn').forEach(b => b.classList.remove('force-switch'));

  await api('POST', '/api/switch', { slot });
  const state = await api('GET', '/api/state');

  const pActive = state.player.slots[state.player.active];
  const eActive = state.enemy.slots[state.enemy.active];

  updateFighterUI('player', pActive);
  updateFighterUI('enemy',  eActive);
  renderPartySlots(state.player);
  renderEnemyParty(state.enemy);
  shakePanel('player');

  appendLog(state.log, () => {
    if (state.phase === 'over') {
      showGameOver(state);
    } else if (state.forceSwitch) {
      showForceSwitchUI();
      busy = false;
    } else {
      renderMoveButtons(pActive.moves, true);
      busy = false;
    }
  });
}

function shakePanel(side) {
  const el = document.getElementById(`${side}-panel`);
  el.classList.remove('shake');
  void el.offsetWidth;
  el.classList.add('shake');
}

function showGameOver(state) {
  const overlay = document.getElementById('game-over-overlay');
  overlay.classList.remove('hidden');
  const won = state.result === 1;
  document.getElementById('go-icon').textContent  = won ? '🏆' : '💀';
  document.getElementById('go-title').textContent = won ? 'VICTORY!' : 'DEFEATED!';
  document.getElementById('go-title').style.color = won ? '#4cf07a' : '#f04c4c';
  document.getElementById('go-sub').textContent   = won
    ? 'You defeated the enemy team!'
    : 'Your team was defeated!';
}

/* ── Move button clicks ──────────────────────── */

document.querySelectorAll('.move-btn').forEach(btn => {
  btn.addEventListener('click', async () => {
    if (busy) return;
    busy = true;
    setButtonsEnabled(false);

    const mi = parseInt(btn.dataset.index, 10);
    await api('POST', '/api/move', { move_index: mi });
    const state = await api('GET', '/api/state');

    const pActive = state.player.slots[state.player.active];
    const eActive = state.enemy.slots[state.enemy.active];

    updateFighterUI('player', pActive);
    updateFighterUI('enemy',  eActive);
    renderPartySlots(state.player);
    renderEnemyParty(state.enemy);
    shakePanel('player');
    shakePanel('enemy');

    appendLog(state.log, () => {
      if (state.phase === 'over') {
        showGameOver(state);
      } else if (state.forceSwitch) {
        showForceSwitchUI();
        busy = false;
      } else {
        renderMoveButtons(pActive.moves, true);
        busy = false;
      }
    });
  });
});

/* ── Play again ──────────────────────────────── */

document.getElementById('btn-play-again').addEventListener('click', async () => {
  await api('POST', '/api/reset');
  document.getElementById('game-over-overlay').classList.add('hidden');
  team            = [null, null, null];
  teamMoves       = [[], [], []];
  currentSlot     = 0;
  selectedPkmnId  = null;
  selectedMoveIds = new Set();
  busy            = false;
  lastLogLen      = 0;
  showScreen('screen-welcome');
});