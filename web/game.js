'use strict';

/* ═══════════════════════════════════════════
   CONSTANTS
   ═══════════════════════════════════════════ */

const TYPE_COLOR = {
  Normal:   '#9BA0A8',
  Fire:     '#FF7035',
  Water:    '#246aff',
  Grass:    '#5ec454',
  Electric: '#f5c518',
  Psychic:  '#f04888',
  Dark:     '#8060a0',
  Rock:     '#976119',
  Fighting: '#d86a25',
  Flying: '#4d8dd2',
  Dragon: '#4e20cb',
  Ice: '#2dcceb',
  Steel: '#c8d8d4',
  Fairy: '#f16cd0',
  Ghost: '#270a75',
  Bug:  '#c2e225',
  Ground: '#ae7557',
  Poison: '#a028b6'

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
  Soarly:    '🦢',
  Drakonyx:  '🐉',
  Freezale:  '❄️',
  Bauxer:    '🧲',
  Faireon:   '✨',
  Spectrowl: '👻',
  Mothra:    '🐞',
  Tecton:    '🛘',
  Cynidor:   '☠️'
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
let teamSize        = 3;
let pvp = false, pickingP2 = false, p1Pick = null;
let pvpStage = 1, p1Move = null, lastState = null, passCallback = null;

// LAN multiplayer: the server holds both picks and resolves a round once
// the second one arrives; each browser just polls /api/state for its own view.
let lan = false, lanRole = null, lanToken = null, lanSeat = 0;
let lanTimer = null, lanTicking = false, lanAnimating = false;
let lanStarted = false, lanRound = 0, lanAwayShown = false, lobbyAction = null;

// What's currently shown on screen for each side, kept in sync line-by-line
// as the battle log plays out, so HP drains / shakes happen exactly when
// their log line appears instead of all at once up front.
let display = { player: null, enemy: null };

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

function showPass(title, sub, onReady) {
  document.getElementById('pass-title').textContent = title;
  document.getElementById('pass-sub').textContent   = sub;
  document.getElementById('pass-overlay').classList.remove('hidden');
  passCallback = onReady;
}
document.getElementById('btn-pass-ready').addEventListener('click', () => {
  document.getElementById('pass-overlay').classList.add('hidden');
  const cb = passCallback; passCallback = null;
  if (cb) cb();
});

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
  { match: /^\[STRUGGLE\]/,           cls: 'struggle' },
  { match: /^\[RECOIL\]/,             cls: 'recoil'  },
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

function appendLog(rawLog, onLine, onDone) {
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

    // Split a leading [TAG] off the line and render it as a small chip,
    // so the log reads like game text rather than a debug dump. The raw
    // line is still what classifyLine() and processLogLine() see, so the
    // colour coding and the animation hooks are unaffected.
    const tag = line.match(/^\[([^\]]+)\]\s*/);
    if (tag) {
      const chip = document.createElement('span');
      chip.className   = 'log-tag';
      chip.textContent = tag[1];
      span.appendChild(chip);
      span.appendChild(document.createTextNode(line.slice(tag[0].length)));
    } else {
      span.textContent = line;
    }

    logDiv.appendChild(span);
    logDiv.appendChild(document.createElement('br'));
    logDiv.scrollTop = logDiv.scrollHeight;
    if (onLine) onLine(line);
    setTimeout(showNext, LOG_DELAY_MS);
  }
  showNext();
}

/* ═══════════════════════════════════════════
   LOG-DRIVEN BATTLE ANIMATION
   ═══════════════════════════════════════════
   Each log line names the fighter(s) involved by name, so we match
   that name against whichever fighter is currently displayed on each
   side to figure out who to animate — then apply the change at the
   moment the line appears, instead of jumping straight to the final
   state before the log has even played. */

function sideForName(name) {
  if (display.player && display.player.name === name) return 'player';
  if (display.enemy  && display.enemy.name  === name) return 'enemy';
  return null;
}

function applyDamageToSide(side, dmg) {
  if (!display[side]) return;
  display[side].hp = Math.max(0, display[side].hp - dmg);
  setHpBar(`${side}-hp-fill`, `${side}-hp-text`, display[side].hp, display[side].maxHp);
  shakePanel(side);
}

function applyStatusToSide(side, status) {
  if (!display[side]) return;
  display[side].status = status;
  document.getElementById(`${side}-status-badge`).innerHTML = statusBadge(status);
}

function applySwitchToSide(side, name, finalState) {
  const party   = side === 'player' ? finalState.player : finalState.enemy;
  const fighter = party.slots.find(f => f && f.name === name);
  if (!fighter) return;
  display[side] = { name: fighter.name, hp: fighter.hp, maxHp: fighter.maxHp, status: fighter.status };
  updateFighterUI(side, fighter);
}

const RE_ATK    = /^\[ATK\] (.+?)->(.+?) : .+? (\d+) dmg!/;
const RE_PSN    = /^\[PSN\] (.+?) took (\d+) poison damage!/;
const RE_BRN    = /^\[BRN\] (.+?) took (\d+) burn damage!/;
const RE_STRUGGLE = /^\[STRUGGLE\] (.+?) used Struggle! \((\d+) dmg\)/;
const RE_RECOIL   = /^\[RECOIL\] (.+?) took (\d+) recoil damage!/;
const RE_STATUS = /^\[STATUS\] (.+?) (?:was burned|was paralysed|was poisoned|fell asleep)!/;
const RE_WAKE   = /^\[WAKE\] (.+?) woke up!/;
const RE_SWITCH_YOU   = /^\[SWITCH\] You sent out (.+?)!/;
const RE_SWITCH_ENEMY = /^\[SWITCH\] Enemy sent out (.+?)!/;

function processLogLine(line, finalState) {
  let m;

  if ((m = line.match(RE_ATK))) {
    const [, , defender, dmg] = m;
    const side = sideForName(defender);
    if (side) applyDamageToSide(side, parseInt(dmg, 10));
    return;
  }
  if ((m = line.match(RE_PSN))) {
    const side = sideForName(m[1]);
    if (side) applyDamageToSide(side, parseInt(m[2], 10));
    return;
  }
  if ((m = line.match(RE_BRN))) {
    const side = sideForName(m[1]);
    if (side) applyDamageToSide(side, parseInt(m[2], 10));
    return;
  }
  if ((m = line.match(RE_STATUS))) {
    const side = sideForName(m[1]);
    if (side) {
      const status = /burned/.test(line) ? 'BURN'
                   : /paralysed/.test(line) ? 'PARALYSIS'
                   : /poisoned/.test(line) ? 'POISON'
                   : /asleep/.test(line) ? 'SLEEP' : 'NONE';
      applyStatusToSide(side, status);
    }
    return;
  }
  if ((m = line.match(RE_WAKE))) {
    const side = sideForName(m[1]);
    if (side) applyStatusToSide(side, 'NONE');
    return;
  }
  if ((m = line.match(RE_SWITCH_YOU))) {
    applySwitchToSide('player', m[1], finalState);
    return;
  }
  if ((m = line.match(RE_SWITCH_ENEMY))) {
    applySwitchToSide('enemy', m[1], finalState);
    return;
  }
  if ((m = line.match(RE_STRUGGLE))) {
    const atkSide = sideForName(m[1]);
    if (atkSide) {
        const defSide = atkSide === 'player' ? 'enemy' : 'player';
        applyDamageToSide(defSide, parseInt(m[2], 10));
    }
    return;
  }
  if ((m = line.match(RE_RECOIL))) {
      const side = sideForName(m[1]);
      if (side) applyDamageToSide(side, parseInt(m[2], 10));
      return;
  }
}

/* ═══════════════════════════════════════════
   SCREEN 1 — WELCOME
   ═══════════════════════════════════════════ */

async function startNewGame(size, isPvp = false, role = null) {
  lanStop();
  lan = role !== null; lanRole = role;
  pvp = isPvp; pickingP2 = false; p1Pick = null; pvpStage = 1; p1Move = null;
  teamSize        = size;
  allPokemon      = await api('GET', '/api/pokemon');
  team            = [null, null, null];
  teamMoves       = [[], [], []];
  currentSlot     = 0;
  selectedPkmnId  = null;
  selectedMoveIds = new Set();
  renderPokemonSelect();
  showScreen('screen-pokemon');
}
document.getElementById('btn-mode-pvp').addEventListener('click', () => startNewGame(1, true));
document.getElementById('btn-mode-3v3').addEventListener('click', () => startNewGame(3));
document.getElementById('btn-mode-1v1').addEventListener('click', () => startNewGame(1));
document.getElementById('btn-mode-host').addEventListener('click', () => {
  lanMessage('');
  startNewGame(1, false, 'host');
});
document.getElementById('btn-mode-join').addEventListener('click', async () => {
  lanMessage('');
  try {
    const info = await api('GET', '/api/lan/info');
    if (!info.lobby) { lanMessage('No game is waiting yet. Ask your friend to host one first.'); return; }
  } catch (e) { lanMessage('Could not reach the server.'); return; }
  startNewGame(1, false, 'guest');
});

/* ═══════════════════════════════════════════
   SCREEN 2 — POKEMON SELECT
   ═══════════════════════════════════════════ */

function renderPokemonSelect() {
  document.getElementById('team-slot-label').textContent = lan ? 'Choose your Pokemon' : pvp ? `Player ${pickingP2 ? 2 : 1}: choose your Pokemon`: `Pick Pokemon ${currentSlot + 1} of ${teamSize}`;

  // team preview circles
  const preview    = document.getElementById('team-preview');
  const pickedIds  = team.filter(Boolean).map(t => t.pid);
  preview.innerHTML = team.slice(0, teamSize).map((t, i) => {
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

  team[currentSlot]      = { pid: selectedPkmnId };
  teamMoves[currentSlot] = [...selectedMoveIds];
  currentSlot++;

  if (currentSlot < teamSize) {
    renderPokemonSelect();
    showScreen('screen-pokemon');
    return;
  }

  if (lan) { await lanSubmitTeam(); return; }

  // PvP: Player 1 is done, hand over to Player 2
  if (pvp && !pickingP2) {
    p1Pick    = { pid: team[0].pid, moves: teamMoves[0] };
    pickingP2 = true;
    team = [null, null, null]; teamMoves = [[], [], []]; currentSlot = 0;
    showPass('PLAYER 2', 'Pass the device to Player 2. Player 1, look away!', () => {
      renderPokemonSelect();
      showScreen('screen-pokemon');
    });
    return;
  }

  const body = { mode: teamSize };
  if (pvp) {
    body.pvp    = 1;
    body.pid0   = p1Pick.pid;    body.moves0   = p1Pick.moves;
    body.p2pid0 = team[0].pid;   body.p2moves0 = teamMoves[0];
  } else {
    for (let i = 0; i < teamSize; i++) {
      body['pid' + i]   = team[i].pid;
      body['moves' + i] = teamMoves[i];
    }
  }
  await api('POST', '/api/start', body);
  const state = await api('GET', '/api/state');
  clearLog();
  document.getElementById('screen-battle').classList.toggle('solo', teamSize === 1);
  showScreen('screen-battle');
  if (pvp) showPass('PLAYER 1', 'Pass the device to Player 1. Battle starts!', () => initBattle(state));
  else     initBattle(state);
});

/* ═══════════════════════════════════════════
   SCREEN 4 — BATTLE
   ═══════════════════════════════════════════ */

function initBattle(state) {
  const pActive = state.player.slots[state.player.active];
  const eActive = state.enemy.slots[state.enemy.active];
  lastState = state;

  display.player = { name: pActive.name, hp: pActive.hp, maxHp: pActive.maxHp, status: pActive.status };
  display.enemy  = { name: eActive.name, hp: eActive.hp, maxHp: eActive.maxHp, status: eActive.status };

  updateFighterUI('player', pActive);
  updateFighterUI('enemy',  eActive);
  renderMoveButtons(pActive.moves, false);
  renderPartySlots(state.player);
  renderEnemyParty(state.enemy);

  appendLog(state.log, line => processLogLine(line, state), () => {
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
  // after the moves.forEach loop
  const allEmpty = moves.every(m => m.pp <= 0);
  if (allEmpty && enabled) {
      const btn = document.getElementById('mbtn-0');
      btn.disabled = false;
      btn.style.borderColor = '#9BA0A850';
      btn.innerHTML = `
          <span class="mbtn-name">STRUGGLE</span>
          <span class="mbtn-meta">Normal &nbsp; PWR:50 &nbsp; Recoil: 25% max HP</span>`;
      for (let i = 1; i < 4; i++)
          document.getElementById(`mbtn-${i}`).disabled = true;
  }
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

  // The switch itself happens immediately (player choice), so show that
  // part right away — only the subsequent counter-attack, if any, should
  // wait for its log line.
  const switchedIn = state.player.slots[state.player.active];
  display.player = { name: switchedIn.name, hp: switchedIn.hp, maxHp: switchedIn.maxHp, status: switchedIn.status };
  updateFighterUI('player', switchedIn);
  renderPartySlots(state.player);

  appendLog(state.log, line => processLogLine(line, state), () => {
    const pActive = state.player.slots[state.player.active];
    const eActive = state.enemy.slots[state.enemy.active];
    display.player = { name: pActive.name, hp: pActive.hp, maxHp: pActive.maxHp, status: pActive.status };
    display.enemy  = { name: eActive.name, hp: eActive.hp, maxHp: eActive.maxHp, status: eActive.status };
    updateFighterUI('player', pActive);
    updateFighterUI('enemy',  eActive);
    renderPartySlots(state.player);
    renderEnemyParty(state.enemy);

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
  document.getElementById('game-over-overlay').classList.remove('hidden');
  const won = state.result === 1;
  document.getElementById('go-icon').textContent  = (pvp || won) ? '🏆' : '💀';
  document.getElementById('go-title').textContent = pvp ? (won ? 'PLAYER 1 WINS!' : 'PLAYER 2 WINS!')
                                                        : (won ? 'VICTORY!' : 'DEFEATED!');
  document.getElementById('go-title').style.color = (pvp || won) ? '#4cf07a' : '#f04c4c';
  document.getElementById('go-sub').textContent   = pvp ? 'Good fight!'
    : lan ? (won ? 'You beat your opponent!' : 'Your opponent won this one.')
    : (won ? 'You defeated the enemy team!' : 'Your team was defeated!');
}

/* ── Move button clicks ──────────────────────── */

document.querySelectorAll('.move-btn').forEach(btn => {
  btn.addEventListener('click', async () => {
    if (busy) return;
    busy = true;
    setButtonsEnabled(false);

    const mi = parseInt(btn.dataset.index, 10);

    if (lan) {
      // Lock the move in and wait; the poll animates the round once both picks are in.
      try { await api('POST', '/api/pick', { token: lanToken, move_index: mi }); }
      catch (e) {
        const me = lastState.player.slots[lastState.player.active];
        renderMoveButtons(me.moves, true);
        busy = false;
        return;
      }
      logNote('▸ Move locked in. Waiting for your opponent...');
      lanTick();
      return;
    }
    if (pvp && pvpStage === 1) {
        p1Move   = mi;
        pvpStage = 2;
        showPass('PLAYER 2', 'Pass the device to Player 2. Player 1, look away!', () => {
          renderMoveButtons(lastState.enemy.slots[lastState.enemy.active].moves, true);
          busy = false;
        });
        return;
      }
    
    const moveBody = pvp ? { move_index: p1Move, move_index2: mi } : { move_index: mi };
    await api('POST', '/api/move', moveBody);
    const state = await api('GET', '/api/state');
    lastState = state; pvpStage = 1;

    appendLog(state.log, line => processLogLine(line, state), () => {
      // Final sync: guarantees the display exactly matches server truth
      // even if any log line's animation was missed above.
      const pActive = state.player.slots[state.player.active];
      const eActive = state.enemy.slots[state.enemy.active];
      display.player = { name: pActive.name, hp: pActive.hp, maxHp: pActive.maxHp, status: pActive.status };
      display.enemy  = { name: eActive.name, hp: eActive.hp, maxHp: eActive.maxHp, status: eActive.status };
      updateFighterUI('player', pActive);
      updateFighterUI('enemy',  eActive);
      renderPartySlots(state.player);
      renderEnemyParty(state.enemy);

      if (state.phase === 'over') {
        showGameOver(state);
      } else if (state.forceSwitch) {
        showForceSwitchUI();
        busy = false;
      } 
      else if (pvp) {
        renderMoveButtons(pActive.moves, false);
        setTimeout(() => showPass('PLAYER 1', 'Round over. Hand the device to Player 1.', () => {
          renderMoveButtons(pActive.moves, true);
          busy = false;
        }), 1200);
      } 
      else {
        renderMoveButtons(pActive.moves, true);
        busy = false;
      }
    });
  });
});

/* ═══════════════════════════════════════════
   LAN MULTIPLAYER
   ═══════════════════════════════════════════ */

function lanMessage(text) { document.getElementById('lan-msg').textContent = text || ''; }

function logNote(text) {
  const logDiv = document.getElementById('battle-log');
  const span   = document.createElement('span');
  span.className   = 'log-line start';
  span.textContent = text;
  logDiv.appendChild(span);
  logDiv.appendChild(document.createElement('br'));
  logDiv.scrollTop = logDiv.scrollHeight;
}

function showLobby(icon, title, sub, label, action) {
  document.getElementById('lobby-icon').textContent  = icon;
  document.getElementById('lobby-title').textContent = title;
  document.getElementById('lobby-sub').textContent   = sub;
  document.getElementById('btn-lobby-action').textContent = label;
  lobbyAction = action;
  document.getElementById('lobby-overlay').classList.remove('hidden');
}
function hideLobby() { document.getElementById('lobby-overlay').classList.add('hidden'); }
document.getElementById('btn-lobby-action').addEventListener('click', () => { if (lobbyAction) lobbyAction(); });

function lanStopPolling() {
  if (lanTimer) clearInterval(lanTimer);
  lanTimer = null; lanTicking = false;
}

function lanStop() {
  lanStopPolling();
  lanAnimating = false; lanStarted = false; lanRound = 0; lanAwayShown = false;
  lanToken = null; lanSeat = 0; lan = false; lanRole = null; lobbyAction = null;
  hideLobby();
}

function lanClosed(msg) {
  lanStop();
  document.getElementById('game-over-overlay').classList.add('hidden');
  busy = false;
  lanMessage(msg);
  showScreen('screen-welcome');
}

async function lanLeave() {
  const tok = lanToken;
  lanStop();
  try { if (tok) await api('POST', '/api/reset', { token: tok }); } catch (e) { /* already gone */ }
  document.getElementById('game-over-overlay').classList.add('hidden');
  busy = false;
  showScreen('screen-welcome');
}

async function lanSubmitTeam() {
  const role = lanRole;
  let res;
  try {
    res = await api('POST', role === 'host' ? '/api/lan/host' : '/api/lan/join',
                    { pid0: team[0].pid, moves0: teamMoves[0] });
  } catch (e) {
    lanStop();
    lanMessage(role === 'host' ? 'Could not start a LAN game. Is one already running?'
                               : 'That game is no longer waiting. Ask the host to start again.');
    showScreen('screen-welcome');
    return;
  }
  lanToken = res.token; lanSeat = res.seat;
  clearLog();
  document.getElementById('screen-battle').classList.add('solo');
  showScreen('screen-battle');

  if (role === 'host') {
    let sub = 'Ask your friend to open this on the same Wi-Fi:';
    try {
      const info = await api('GET', '/api/lan/info');
      sub += `\nhttp://${info.ip}:${info.port}/?join`;
      if (info.ips.length > 1) sub += `\n(if that fails, try: ${info.ips.slice(1).join(', ')})`;
    } catch (e) { sub += '\n(could not work out this PC\'s address)'; }
    showLobby('📡', 'WAITING FOR PLAYER 2', sub, 'Cancel', lanLeave);
  }
  lanTimer = setInterval(lanTick, 800);
  lanTick();
}

async function lanTick() {
  if (!lan || !lanToken || lanTicking || lanAnimating) return;
  lanTicking = true;
  let state;
  try { state = await api('GET', '/api/state?t=' + encodeURIComponent(lanToken)); }
  catch (e) {
    lanTicking = false;
    if (/ 403 /.test(e.message)) lanClosed('The LAN game was closed.');
    return;
  }
  lanTicking = false;
  if (!lan) return;                                  // left while the request was in flight

  if (state.phase === 'idle')  { lanClosed('The LAN game was closed.'); return; }
  if (state.phase === 'lobby') return;

  if (state.away && !lanAwayShown) {
    lanAwayShown = true;
    showLobby('📴', 'OPPONENT DISCONNECTED', 'Waiting for them to come back...', 'Leave game', lanLeave);
  } else if (!state.away && lanAwayShown) {
    lanAwayShown = false; hideLobby();
  }

  if (!lanStarted) {                                 // opponent joined: start the battle view
    lanStarted = true; lanRound = state.round;
    hideLobby();
    lastState = state;
    initBattle(state);
    return;
  }
  if (state.round > lanRound) {                      // a round was resolved: play it back
    lanRound = state.round; lanAnimating = true; lastState = state;
    appendLog(state.log, line => processLogLine(line, state), () => lanFinishRound(state));
  }
}

function lanFinishRound(state) {
  const pActive = state.player.slots[state.player.active];
  const eActive = state.enemy.slots[state.enemy.active];
  display.player = { name: pActive.name, hp: pActive.hp, maxHp: pActive.maxHp, status: pActive.status };
  display.enemy  = { name: eActive.name, hp: eActive.hp, maxHp: eActive.maxHp, status: eActive.status };
  updateFighterUI('player', pActive);
  updateFighterUI('enemy',  eActive);
  renderPartySlots(state.player);
  renderEnemyParty(state.enemy);
  lanAnimating = false;
  if (state.phase === 'over') { lanStopPolling(); showGameOver(state); }
  else { renderMoveButtons(pActive.moves, true); busy = false; }
}

// Opening http://<host-ip>:8080/?join jumps straight to joining
if (/[?&]join\b/.test(location.search)) document.getElementById('btn-mode-join').click();

/* ── Play again ──────────────────────────────── */

document.getElementById('btn-play-again').addEventListener('click', async () => {
  if (!lan) await api('POST', '/api/reset');   // an online game is replaced by the next host
  lanStop();
  document.getElementById('game-over-overlay').classList.add('hidden');
  team            = [null, null, null];
  teamMoves       = [[], [], []];
  currentSlot     = 0;
  selectedPkmnId  = null;
  selectedMoveIds = new Set();
  busy            = false;
  lastLogLen      = 0;
  display         = { player: null, enemy: null };
  showScreen('screen-welcome');
});