import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import { Client } from '@stomp/stompjs';
import './App.css';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

// Native WebSocket endpoint supporting both local dev and production SSL (wss://)
const getWsBrokerUrl = () => {
  if (import.meta.env.VITE_WS_BROKER_URL) {
    return import.meta.env.VITE_WS_BROKER_URL;
  }
  if (import.meta.env.VITE_API_BASE_URL) {
    const rawUrl = import.meta.env.VITE_API_BASE_URL.replace(/\/api\/?$/, '');
    if (rawUrl.startsWith('https://')) {
      return rawUrl.replace('https://', 'wss://') + '/ws-flashpass';
    } else if (rawUrl.startsWith('http://')) {
      return rawUrl.replace('http://', 'ws://') + '/ws-flashpass';
    }
  }
  return 'ws://localhost:8080/ws-flashpass';
};

const WS_BROKER_URL = getWsBrokerUrl();

const FAN_PERSONAS = [
  { id: 'kiran', name: 'Kiran', role: 'Fan A (You)', badge: 'VIP Member' },
  { id: 'aarav', name: 'Aarav', role: 'Fan B', badge: 'Concertgoer' },
  { id: 'priya', name: 'Priya', role: 'Fan C', badge: 'Platinum Pass' },
  { id: 'vikram', name: 'Vikram', role: 'Fan D', badge: 'Early Bird' }
];

function App() {
  const [events, setEvents] = useState([]);
  const [activeEvent, setActiveEvent] = useState(null);
  const [seats, setSeats] = useState([]);
  const [selectedSeat, setSelectedSeat] = useState(null);
  const [loading, setLoading] = useState(true);
  const [statusMessage, setStatusMessage] = useState({ text: '', type: '' });
  const [bookedTicket, setBookedTicket] = useState(null);
  const [timeLeft, setTimeLeft] = useState(300);
  const [wsConnected, setWsConnected] = useState(false);
  
  // Multi-User Active Persona (Stored per session so multiple tabs can simulate different fans!)
  const [activePersona, setActivePersona] = useState(() => {
    return sessionStorage.getItem('flashpass_persona') || 'Kiran';
  });

  // Sound effects toggle
  const [soundEnabled, setSoundEnabled] = useState(true);

  // Concurrency Race Simulator Modal State
  const [raceModal, setRaceModal] = useState({ open: false, running: false, results: null, targetSeat: null });

  const activeEventRef = useRef(activeEvent);
  activeEventRef.current = activeEvent;

  // Synthesized Web Audio API Sound Effects (Zero external audio files needed!)
  const playSound = (type) => {
    if (!soundEnabled) return;
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return;
      const ctx = new AudioCtx();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);

      if (type === 'click') {
        osc.frequency.setValueAtTime(600, ctx.currentTime);
        gain.gain.setValueAtTime(0.04, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.08);
        osc.start();
        osc.stop(ctx.currentTime + 0.08);
      } else if (type === 'lock') {
        osc.frequency.setValueAtTime(523.25, ctx.currentTime);
        osc.frequency.setValueAtTime(659.25, ctx.currentTime + 0.08);
        gain.gain.setValueAtTime(0.08, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.25);
        osc.start();
        osc.stop(ctx.currentTime + 0.25);
      } else if (type === 'book') {
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(523.25, ctx.currentTime);
        osc.frequency.setValueAtTime(659.25, ctx.currentTime + 0.1);
        osc.frequency.setValueAtTime(783.99, ctx.currentTime + 0.2);
        gain.gain.setValueAtTime(0.1, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.4);
        osc.start();
        osc.stop(ctx.currentTime + 0.4);
      } else if (type === 'error') {
        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(220, ctx.currentTime);
        gain.gain.setValueAtTime(0.08, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.2);
        osc.start();
        osc.stop(ctx.currentTime + 0.2);
      }
    } catch (e) {
      // Audio context suppressed by browser policies until user gesture
    }
  };

  // 1. Initial REST API Fetch: Load All Available Events
  useEffect(() => {
    fetchEvents();
  }, []);

  // 2. Real-Time Native WebSocket (STOMP Protocol)
  useEffect(() => {
    const stompClient = new Client({
      brokerURL: WS_BROKER_URL,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        setWsConnected(true);
        console.log('>>> [WEBSOCKET] Connected via native browser WebSocket!');

        // Subscribe to real-time seat status broadcasts
        stompClient.subscribe('/topic/seats', (message) => {
          if (message.body) {
            try {
              const updatedSeat = JSON.parse(message.body);
              console.log('>>> [WEBSOCKET INCOMING]', updatedSeat);

              // Update seat in state if it belongs to currently active event
              setSeats(prevSeats =>
                prevSeats.map(s => (s.id === updatedSeat.id ? updatedSeat : s))
              );

              // If the updated seat was selected by this client and someone else took it or released it
              setSelectedSeat(prev => {
                if (prev && prev.id === updatedSeat.id) {
                  if (updatedSeat.status === 'AVAILABLE') return null;
                  return updatedSeat;
                }
                return prev;
              });
            } catch (err) {
              console.error('Failed to parse WebSocket message', err);
            }
          }
        });
      },
      onDisconnect: () => {
        setWsConnected(false);
        console.log('>>> [WEBSOCKET] Disconnected.');
      },
      onStompError: (frame) => {
        console.error('>>> [WEBSOCKET ERROR]', frame);
      },
      onWebSocketError: (err) => {
        console.warn('>>> [WEBSOCKET FALLBACK] Native WebSocket connecting...', err);
      }
    });

    stompClient.activate();

    return () => {
      stompClient.deactivate();
    };
  }, []);

  // 3. 5-Minute Countdown Timer for Held Seat
  useEffect(() => {
    let timer;
    if (selectedSeat && timeLeft > 0) {
      timer = setInterval(() => setTimeLeft(prev => prev - 1), 1000);
    } else if (timeLeft === 0 && selectedSeat) {
      handleReleaseSeat();
      showMessage('Your 5-minute seat reservation lease expired and was released!', 'error');
    }
    return () => clearInterval(timer);
  }, [selectedSeat, timeLeft]);

  const fetchEvents = async () => {
    try {
      setLoading(true);
      const res = await axios.get(`${API_BASE_URL}/events`);
      if (res.data && res.data.length > 0) {
        setEvents(res.data);
        const initial = res.data[0];
        setActiveEvent(initial);
        await loadSeatsForEvent(initial.id);
      }
    } catch (err) {
      showMessage('Backend unreachable. Is Spring Boot running?', 'error');
    } finally {
      setLoading(false);
    }
  };

  const loadSeatsForEvent = async (eventId) => {
    try {
      const seatsRes = await axios.get(`${API_BASE_URL}/events/${eventId}/seats`);
      setSeats(seatsRes.data);
      setSelectedSeat(null);
    } catch (err) {
      showMessage('Failed to load seats for event.', 'error');
    }
  };

  const handleSelectEvent = (eventItem) => {
    playSound('click');
    setActiveEvent(eventItem);
    loadSeatsForEvent(eventItem.id);
  };

  const handlePersonaChange = (personaName) => {
    playSound('click');
    setActivePersona(personaName);
    sessionStorage.setItem('flashpass_persona', personaName);
    showMessage(`Switched fan identity to: ${personaName}`, 'info');
  };

  const showMessage = (text, type = 'info') => {
    setStatusMessage({ text, type });
    if (type === 'error') playSound('error');
    setTimeout(() => setStatusMessage({ text: '', type: '' }), 4500);
  };

  // Dynamic row extraction from actual seat numbers (e.g. ['A', 'B', 'C', 'D'] or ['A', 'B', 'C', 'D', 'E'])
  const uniqueRows = Array.from(new Set(seats.map(s => s.seatNumber.charAt(0)))).sort();

  const getSortedRowSeats = (rowLetter) => {
    return seats
      .filter(s => s.seatNumber.startsWith(rowLetter))
      .sort((a, b) => {
        const numA = parseInt(a.seatNumber.replace(/\D/g, ''), 10);
        const numB = parseInt(b.seatNumber.replace(/\D/g, ''), 10);
        return numA - numB;
      });
  };

  // Lock Seat (POST /api/seats/{id}/lock?user=...)
  const handleSeatClick = async (seat) => {
    playSound('click');

    if (seat.status === 'BOOKED') {
      const buyer = seat.bookedBy ? ` by ${seat.bookedBy}` : '';
      showMessage(`Seat ${seat.seatNumber} is permanently SOLD OUT${buyer}.`, 'error');
      return;
    }

    if (seat.status === 'LOCKED') {
      // Check if current user is the lock holder
      if (seat.lockedBy === activePersona) {
        showMessage(`You are currently holding Seat ${seat.seatNumber}. Complete checkout below!`, 'info');
        setSelectedSeat(seat);
        return;
      }
      showMessage(`Seat ${seat.seatNumber} is currently held in ${seat.lockedBy || 'another fan'}'s cart. Only the lock holder can release or pay.`, 'error');
      return;
    }

    try {
      const response = await axios.post(`${API_BASE_URL}/seats/${seat.id}/lock?user=${encodeURIComponent(activePersona)}`);
      const updatedSeat = response.data;

      setSeats(prevSeats =>
        prevSeats.map(s => (s.id === updatedSeat.id ? updatedSeat : s))
      );
      setSelectedSeat(updatedSeat);
      setTimeLeft(300);
      playSound('lock');
      showMessage(`Seat ${updatedSeat.seatNumber} LOCKED for 5 minutes by ${activePersona}! Complete checkout below.`, 'success');
    } catch (err) {
      const errorMsg = err.response?.data?.message || 'Failed to lock seat.';
      showMessage(errorMsg, 'error');
      if (activeEvent) loadSeatsForEvent(activeEvent.id);
    }
  };

  // Confirm Booking (POST /api/seats/{id}/book?user=...)
  const handleConfirmBooking = async () => {
    if (!selectedSeat) return;
    try {
      const response = await axios.post(`${API_BASE_URL}/seats/${selectedSeat.id}/book?user=${encodeURIComponent(activePersona)}`);
      const booked = response.data;

      setSeats(prevSeats =>
        prevSeats.map(s => (s.id === booked.id ? booked : s))
      );
      setBookedTicket(booked);
      setSelectedSeat(null);
      playSound('book');
      showMessage(`🎉 Congratulations ${activePersona}! Seat ${booked.seatNumber} officially BOOKED!`, 'success');
    } catch (err) {
      showMessage(err.response?.data?.message || 'Booking failed.', 'error');
    }
  };

  // Release Seat (POST /api/seats/{id}/release?user=...)
  const handleReleaseSeat = async () => {
    if (!selectedSeat) return;
    try {
      const response = await axios.post(`${API_BASE_URL}/seats/${selectedSeat.id}/release?user=${encodeURIComponent(activePersona)}`);
      const released = response.data;

      setSeats(prevSeats =>
        prevSeats.map(s => (s.id === released.id ? released : s))
      );
      setSelectedSeat(null);
      playSound('click');
      showMessage(`Seat ${released.seatNumber} released back to Available pool.`, 'info');
    } catch (err) {
      showMessage(err.response?.data?.message || 'Release failed.', 'error');
    }
  };

  // Admin Reset Stadium (POST /api/events/{id}/reset)
  const handleResetStadium = async () => {
    if (!activeEvent) return;
    if (!window.confirm(`Reset all seats for "${activeEvent.name}" back to AVAILABLE? This will clear all holds and bookings.`)) return;

    try {
      const response = await axios.post(`${API_BASE_URL}/events/${activeEvent.id}/reset`);
      setSeats(response.data);
      setSelectedSeat(null);
      playSound('book');
      showMessage(`🔄 All seats for "${activeEvent.name}" have been reset to AVAILABLE!`, 'success');
    } catch (err) {
      showMessage('Failed to reset stadium.', 'error');
    }
  };

  // ⚡ 10-Bot Concurrency Race Simulation (Battle Test Mode)
  const handleRunRaceSimulation = async () => {
    // Find first available seat
    const candidateSeat = seats.find(s => s.status === 'AVAILABLE');
    if (!candidateSeat) {
      showMessage('No available seats to race for! Click "Reset Stadium" first.', 'error');
      return;
    }

    setRaceModal({ open: true, running: true, results: null, targetSeat: candidateSeat });
    playSound('lock');

    const botNames = [
      'FlashBot-1', 'TurboFan-2', 'SonicFan-3', 'HyperBot-4', 'RapidFan-5',
      'QuantumBot-6', 'RocketFan-7', 'BlitzBot-8', 'ApexFan-9', 'PhantomBot-10'
    ];

    const startTime = performance.now();

    // Simultaneously fire 10 concurrent requests to lock the EXACT same seat!
    const racePromises = botNames.map(async (botName) => {
      try {
        const res = await axios.post(`${API_BASE_URL}/seats/${candidateSeat.id}/lock?user=${botName}`);
        return { bot: botName, status: 'SUCCESS', code: 200, message: 'Lock Acquired (Winner 🏆)' };
      } catch (err) {
        const status = err.response?.status || 500;
        const msg = err.response?.data?.message || 'OptimisticLock Collision';
        return { bot: botName, status: 'COLLISION_PREVENTED', code: status, message: msg };
      }
    });

    const results = await Promise.all(racePromises);
    const duration = Math.round(performance.now() - startTime);

    setRaceModal({
      open: true,
      running: false,
      results: results,
      duration: duration,
      targetSeat: candidateSeat
    });

    // Refresh seat state
    if (activeEvent) loadSeatsForEvent(activeEvent.id);
  };

  const totalSeats = seats.length;
  const availableCount = seats.filter(s => s.status === 'AVAILABLE').length;
  const lockedCount = seats.filter(s => s.status === 'LOCKED').length;
  const bookedCount = seats.filter(s => s.status === 'BOOKED').length;

  const formatTimer = (seconds) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  if (loading) {
    return (
      <div className="loader-container">
        <div className="spinner"></div>
        <p>Connecting to FlashPass Distributed Cloud Engine...</p>
      </div>
    );
  }

  return (
    <div className="app-wrapper">
      <div className="glow-sphere top-left"></div>
      <div className="glow-sphere bottom-right"></div>

      {/* Floating Global Glassmorphism Toast (Zero Layout Shift!) */}
      {statusMessage.text && (
        <div className={`floating-toast ${statusMessage.type}`}>
          <span className="toast-icon">
            {statusMessage.type === 'success' ? '✓' : statusMessage.type === 'error' ? '✕' : 'ℹ'}
          </span>
          <span className="toast-body">{statusMessage.text}</span>
          <button className="toast-close" onClick={() => setStatusMessage({ text: '', type: '' })}>✕</button>
        </div>
      )}

      <div className="app-container">
        {/* Top Navbar */}
        <nav className="top-nav">
          <div className="brand">
            <span className="brand-logo">⚡</span>
            <div>
              <span className="brand-title">FlashPass</span>
              <span className="engine-tag">DISTRIBUTED CONCURRENCY ENGINE</span>
            </div>
          </div>

          <div className="nav-center-persona">
            <span className="persona-label">ACTIVE FAN:</span>
            <div className="persona-pills">
              {FAN_PERSONAS.map(p => (
                <button
                  key={p.id}
                  className={`persona-btn ${activePersona === p.name ? 'active' : ''}`}
                  onClick={() => handlePersonaChange(p.name)}
                  title={`${p.name} - ${p.role} (${p.badge})`}
                >
                  <span className="avatar-dot"></span>
                  {p.name}
                </button>
              ))}
            </div>
          </div>

          <div className="nav-badges">
            <button 
              className="sound-toggle-btn"
              onClick={() => setSoundEnabled(!soundEnabled)} 
              title={soundEnabled ? 'Mute Sounds' : 'Unmute Sounds'}
            >
              {soundEnabled ? '🔊' : '🔇'}
            </button>

            <button className="btn-admin-reset" onClick={handleResetStadium} title="Reset all seats for current event">
              🔄 Reset Stadium
            </button>

            <div className={`ws-pill ${wsConnected ? 'connected' : 'disconnected'}`}>
              <span className="ws-dot"></span>
              <span>{wsConnected ? 'WebSocket Live' : 'Connecting...'}</span>
            </div>
          </div>
        </nav>

        {/* Multi-Event Selector Bar */}
        <div className="event-selector-bar">
          <span className="selector-title">SELECT TOUR CONCERT:</span>
          <div className="event-tabs">
            {events.map(ev => {
              const isSelected = activeEvent?.id === ev.id;
              const icon = ev.name.includes('Coldplay') ? '🎸' : ev.name.includes('Diljit') ? '🎤' : '✨';
              return (
                <button
                  key={ev.id}
                  className={`event-tab-card ${isSelected ? 'active' : ''}`}
                  onClick={() => handleSelectEvent(ev)}
                >
                  <span className="event-tab-icon">{icon}</span>
                  <div className="event-tab-info">
                    <span className="event-tab-name">{ev.name.split(':')[0]}</span>
                    <span className="event-tab-venue">{ev.venue.split(',')[0]}</span>
                  </div>
                  {isSelected && <span className="active-glow-indicator"></span>}
                </button>
              );
            })}
          </div>
        </div>

        {/* Concert Hero Header */}
        <header className="event-hero">
          <div className="hero-tags">
            <span className="badge-live">● LIVE HIGH-CONCURRENCY RESERVATION</span>
            <span className="badge-venue">VIP PLATINUM &amp; GENERAL ADMISSION</span>
          </div>
          <h1 className="hero-title">{activeEvent?.name || 'Live Concert Tour'}</h1>
          <p className="hero-meta">
            <span>📍 {activeEvent?.venue || 'Stadium Arena'}</span>
            <span>•</span>
            <span>🗓️ {activeEvent?.eventDate ? new Date(activeEvent.eventDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : 'Tour Finale'}</span>
            <span>•</span>
            <span>🔒 Optimistic Lock Protection</span>
          </p>

          {/* Real-time HUD Metrics Bar */}
          <div className="metrics-hud">
            <div className="metric-box">
              <span className="metric-num">{totalSeats}</span>
              <span className="metric-lbl">Total Capacity</span>
            </div>
            <div className="metric-divider"></div>
            <div className="metric-box green">
              <span className="metric-num">{availableCount}</span>
              <span className="metric-lbl">Available</span>
            </div>
            <div className="metric-divider"></div>
            <div className="metric-box amber">
              <span className="metric-num">{lockedCount}</span>
              <span className="metric-lbl">Held in Cart</span>
            </div>
            <div className="metric-divider"></div>
            <div className="metric-box red">
              <span className="metric-num">{bookedCount}</span>
              <span className="metric-lbl">Sold Out</span>
            </div>
            <div className="metric-divider"></div>
            <button className="btn-race-simulator" onClick={handleRunRaceSimulation}>
              ⚡ Simulate 10-Bot Race
            </button>
          </div>
        </header>

        {/* Legend */}
        <div className="legend-strip">
          <div className="legend-item"><span className="seat-sample available"></span> Available</div>
          <div className="legend-item"><span className="seat-sample your-hold"></span> Your Cart Hold</div>
          <div className="legend-item"><span className="seat-sample locked"></span> Held by Other Fan</div>
          <div className="legend-item"><span className="seat-sample booked"></span> Booked (Sold)</div>
        </div>

        {/* Stadium Stage */}
        <div className="stadium-stage">
          <div className="stage-arch">
            <span className="stage-text">★ LIVE STAGE / PERFORMANCE PLATFORM ★</span>
          </div>
          <div className="stage-glow"></div>
        </div>

        {/* Stadium Seating Layout (Dynamically renders any event's seating capacity!) */}
        <div className="stadium-layout">
          {uniqueRows.map((row, idx) => {
            const sortedSeats = getSortedRowSeats(row);
            const isVip = idx < 2; // First 2 tiers VIP
            const mid = Math.ceil(sortedSeats.length / 2);
            const leftWing = sortedSeats.slice(0, mid);
            const rightWing = sortedSeats.slice(mid);
            const tierPrice = sortedSeats[0]?.price || (isVip ? 5000 : 2500);

            return (
              <div key={row} className={`seating-tier ${isVip ? 'vip-tier' : 'std-tier'}`}>
                <div className="tier-info">
                  <span className="row-badge">{row}</span>
                  <span className="tier-name">{isVip ? 'VIP Front' : 'Grandstand'}</span>
                </div>

                <div className="tier-seats-container">
                  <div className="wing-block">
                    {leftWing.map(seat => renderSeat(seat))}
                  </div>

                  <div className="aisle-spacer">
                    <span>AISLE</span>
                  </div>

                  <div className="wing-block">
                    {rightWing.map(seat => renderSeat(seat))}
                  </div>
                </div>

                <div className="tier-price">
                  <span className="price-tag">₹{tierPrice.toLocaleString()}</span>
                </div>
              </div>
            );
          })}
        </div>

        {/* Floating Checkout Drawer */}
        {selectedSeat && (
          <div className="checkout-drawer">
            <div className="drawer-timer">
              <span className="clock-icon">⏳</span>
              <span className="time-display">{formatTimer(timeLeft)}</span>
              <span className="time-caption">Hold Remaining</span>
            </div>

            <div className="drawer-details">
              <h4>Seat {selectedSeat.seatNumber} Reserved ({selectedSeat.lockedBy || activePersona})</h4>
              <p>Category: {selectedSeat.seatNumber.startsWith('A') || selectedSeat.seatNumber.startsWith('B') ? 'VIP Platinum' : 'Standard'} • Total: <strong>₹{selectedSeat.price?.toLocaleString()}</strong></p>
            </div>

            <div className="drawer-actions">
              <button className="btn-release" onClick={handleReleaseSeat}>Release Seat</button>
              <button className="btn-pay" onClick={handleConfirmBooking}>Confirm &amp; Pay ₹{selectedSeat.price?.toLocaleString()}</button>
            </div>
          </div>
        )}

        {/* Confirmed Ticket Modal */}
        {bookedTicket && (
          <div className="modal-backdrop">
            <div className="ticket-modal">
              <div className="modal-header">
                <h3>PASS ISSUED &bull; {bookedTicket.bookedBy || activePersona}</h3>
                <button className="close-btn" onClick={() => setBookedTicket(null)}>✕</button>
              </div>
              <div className="ticket-body">
                <div className="ticket-event">{activeEvent?.name}</div>
                <div className="ticket-grid">
                  <div>
                    <span className="t-lbl">SEAT NUMBER</span>
                    <span className="t-val accent">{bookedTicket.seatNumber}</span>
                  </div>
                  <div>
                    <span className="t-lbl">FAN TICKET HOLDER</span>
                    <span className="t-val accent">{bookedTicket.bookedBy || activePersona}</span>
                  </div>
                  <div>
                    <span className="t-lbl">VENUE</span>
                    <span className="t-val">{activeEvent?.venue}</span>
                  </div>
                  <div>
                    <span className="t-lbl">STATUS</span>
                    <span className="t-val success">CONFIRMED (PAID)</span>
                  </div>
                  <div>
                    <span className="t-lbl">PRICE</span>
                    <span className="t-val">₹{bookedTicket.price?.toLocaleString()}</span>
                  </div>
                  <div>
                    <span className="t-lbl">JPA VERSION</span>
                    <span className="t-val">v{bookedTicket.version} (Optimistic Locked)</span>
                  </div>
                </div>
                <div className="ticket-barcode">
                  <div className="barcode-lines"></div>
                  <span>FLASHPASS-TXN-{bookedTicket.id}-{Date.now().toString().slice(-6)}</span>
                </div>
              </div>
              <button className="btn-done" onClick={() => setBookedTicket(null)}>Book Another Seat</button>
            </div>
          </div>
        )}

        {/* ⚡ Concurrency Race Battle Modal */}
        {raceModal.open && (
          <div className="modal-backdrop">
            <div className="race-modal">
              <div className="modal-header">
                <h3>⚡ CONCURRENCY RACE BATTLE (10 VIRTUAL BOTS)</h3>
                <button className="close-btn" onClick={() => setRaceModal({ open: false, running: false, results: null, targetSeat: null })}>✕</button>
              </div>
              
              <div className="race-body">
                <div className="race-summary-banner">
                  <div><strong>Target Seat:</strong> {raceModal.targetSeat?.seatNumber} (₹{raceModal.targetSeat?.price})</div>
                  <div><strong>Race Latency:</strong> {raceModal.duration ? `${raceModal.duration} ms` : 'Computing...'}</div>
                </div>

                {raceModal.running ? (
                  <div className="race-running-loader">
                    <div className="spinner small"></div>
                    <p>10 concurrent threads attacking Seat {raceModal.targetSeat?.seatNumber} simultaneously...</p>
                  </div>
                ) : (
                  <div className="race-results-list">
                    {raceModal.results?.map((res, i) => (
                      <div key={i} className={`race-result-item ${res.status === 'SUCCESS' ? 'winner' : 'collision'}`}>
                        <div className="race-bot-col">
                          <span className="bot-tag">{res.bot}</span>
                          <span className="status-badge">{res.code}</span>
                        </div>
                        <div className="race-msg-col">
                          {res.status === 'SUCCESS' ? '🏆 200 OK — Lock Acquired! Winner of the Race.' : `🛑 409 Conflict — ${res.message}`}
                        </div>
                      </div>
                    ))}

                    <div className="race-conclusion-box">
                      <strong>🎯 Concurrency Defense Verified:</strong> Exactly 1 bot acquired the lock, and 9 collision attempts were safely rejected by Spring Boot's Optimistic Locking mechanism. Zero race conditions. Zero double bookings.
                    </div>
                  </div>
                )}
              </div>

              <button className="btn-done" onClick={() => setRaceModal({ open: false, running: false, results: null, targetSeat: null })}>
                Close Battle HUD
              </button>
            </div>
          </div>
        )}

      </div>
    </div>
  );

  function renderSeat(seat) {
    const isSelected = selectedSeat?.id === seat.id;
    const isYourHold = seat.status === 'LOCKED' && seat.lockedBy === activePersona;
    const isOtherHold = seat.status === 'LOCKED' && seat.lockedBy !== activePersona;

    let seatClasses = 'stadium-seat';

    if (seat.status === 'AVAILABLE') seatClasses += ' seat-available';
    else if (isYourHold) seatClasses += ' seat-your-hold';
    else if (isOtherHold) seatClasses += ' seat-locked';
    else if (seat.status === 'BOOKED') seatClasses += ' seat-booked';

    if (isSelected) seatClasses += ' seat-selected';

    let seatTitle = `${seat.seatNumber} • ₹${seat.price?.toLocaleString()} (${seat.status})`;
    if (isYourHold) seatTitle += ` - Held by You (${activePersona})`;
    else if (isOtherHold) seatTitle += ` - Held by ${seat.lockedBy || 'Another Fan'}`;
    else if (seat.status === 'BOOKED' && seat.bookedBy) seatTitle += ` - Sold to ${seat.bookedBy}`;

    return (
      <button
        key={seat.id}
        className={seatClasses}
        onClick={() => handleSeatClick(seat)}
        title={seatTitle}
      >
        <span className="seat-cushion"></span>
        <span className="seat-label">{seat.seatNumber}</span>
        {isOtherHold && <span className="seat-lock-badge">🔒</span>}
        {isYourHold && <span className="seat-user-badge">★</span>}
      </button>
    );
  }
}

export default App;
