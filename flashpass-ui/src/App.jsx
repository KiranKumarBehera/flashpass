import React, { useState, useEffect } from 'react';
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

function App() {
  const [event, setEvent] = useState(null);
  const [seats, setSeats] = useState([]);
  const [selectedSeat, setSelectedSeat] = useState(null);
  const [loading, setLoading] = useState(true);
  const [statusMessage, setStatusMessage] = useState({ text: '', type: '' });
  const [bookedTicket, setBookedTicket] = useState(null);
  const [timeLeft, setTimeLeft] = useState(300);
  const [wsConnected, setWsConnected] = useState(false);

  // 1. Initial REST API Fetch on Page Load
  useEffect(() => {
    fetchEventAndSeats();
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

              // Update seat in local state immediately without full page reload!
              setSeats(prevSeats =>
                prevSeats.map(s => (s.id === updatedSeat.id ? updatedSeat : s))
              );
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

  const fetchEventAndSeats = async () => {
    try {
      setLoading(true);
      const eventRes = await axios.get(`${API_BASE_URL}/events`);
      if (eventRes.data.length > 0) {
        setEvent(eventRes.data[0]);
        const seatsRes = await axios.get(`${API_BASE_URL}/events/${eventRes.data[0].id}/seats`);
        setSeats(seatsRes.data);
      }
    } catch (err) {
      showMessage('Backend unreachable. Is Spring Boot running on port 8080?', 'error');
    } finally {
      setLoading(false);
    }
  };

  const showMessage = (text, type = 'info') => {
    setStatusMessage({ text, type });
    setTimeout(() => setStatusMessage({ text: '', type: '' }), 4000);
  };

  // Natural numeric sorting (A1 through A10)
  const getSortedRowSeats = (rowLetter) => {
    return seats
      .filter(s => s.seatNumber.startsWith(rowLetter))
      .sort((a, b) => {
        const numA = parseInt(a.seatNumber.replace(/\D/g, ''), 10);
        const numB = parseInt(b.seatNumber.replace(/\D/g, ''), 10);
        return numA - numB;
      });
  };

  // Lock Seat (POST /api/seats/{id}/lock)
  const handleSeatClick = async (seat) => {
    if (seat.status === 'BOOKED') {
      showMessage(`Seat ${seat.seatNumber} is already permanently SOLD OUT.`, 'error');
      return;
    }
    if (seat.status === 'LOCKED') {
      if (selectedSeat && selectedSeat.id === seat.id) return;
      showMessage(`Seat ${seat.seatNumber} is currently held by another user.`, 'error');
      return;
    }

    try {
      const response = await axios.post(`${API_BASE_URL}/seats/${seat.id}/lock`);
      const updatedSeat = response.data;

      setSeats(prevSeats =>
        prevSeats.map(s => (s.id === updatedSeat.id ? updatedSeat : s))
      );
      setSelectedSeat(updatedSeat);
      setTimeLeft(300);
      showMessage(`Seat ${updatedSeat.seatNumber} LOCKED for 5 minutes! Complete checkout below.`, 'success');
    } catch (err) {
      const errorMsg = err.response?.data?.message || 'Failed to lock seat.';
      showMessage(errorMsg, 'error');
      fetchEventAndSeats();
    }
  };

  // Confirm Booking (POST /api/seats/{id}/book)
  const handleConfirmBooking = async () => {
    if (!selectedSeat) return;
    try {
      const response = await axios.post(`${API_BASE_URL}/seats/${selectedSeat.id}/book`);
      const booked = response.data;

      setSeats(prevSeats =>
        prevSeats.map(s => (s.id === booked.id ? booked : s))
      );
      setBookedTicket(booked);
      setSelectedSeat(null);
      showMessage(`🎉 Congratulations! Seat ${booked.seatNumber} officially BOOKED!`, 'success');
    } catch (err) {
      showMessage(err.response?.data?.message || 'Booking failed.', 'error');
    }
  };

  // Release Seat (POST /api/seats/{id}/release)
  const handleReleaseSeat = async () => {
    if (!selectedSeat) return;
    try {
      const response = await axios.post(`${API_BASE_URL}/seats/${selectedSeat.id}/release`);
      const released = response.data;

      setSeats(prevSeats =>
        prevSeats.map(s => (s.id === released.id ? released : s))
      );
      setSelectedSeat(null);
      showMessage(`Seat ${released.seatNumber} released back to Available pool.`, 'info');
    } catch (err) {
      showMessage(err.response?.data?.message || 'Release failed.', 'error');
    }
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

      <div className="app-container">
        {/* Top Navbar */}
        <nav className="top-nav">
          <div className="brand">
            <span className="brand-logo">⚡</span>
            <span className="brand-title">FlashPass</span>
            <span className="engine-tag">DISTRIBUTED CONCURRENCY ENGINE</span>
          </div>

          <div className="nav-badges">
            <div className={`ws-pill ${wsConnected ? 'connected' : 'disconnected'}`}>
              <span className="ws-dot"></span>
              <span>{wsConnected ? 'WebSocket Live (Real-Time)' : 'WebSocket Connecting...'}</span>
            </div>

            <div className="cache-pill">
              <span className="pulse-dot"></span>
              <span>Upstash Redis: <strong>&lt;10ms</strong></span>
            </div>
          </div>
        </nav>

        {/* Concert Header */}
        <header className="event-hero">
          <div className="hero-tags">
            <span className="badge-live">● LIVE HIGH-CONCURRENCY RESERVATION</span>
            <span className="badge-venue">VIP PLATINUM &amp; GENERAL ADMISSION</span>
          </div>
          <h1 className="hero-title">{event?.name || 'Coldplay: Music of the Spheres World Tour'}</h1>
          <p className="hero-meta">
            <span>📍 {event?.venue || 'DY Patil Stadium, Mumbai'}</span>
            <span>•</span>
            <span>🗓️ Tour Finale</span>
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
          </div>
        </header>

        {/* Toast Alerts */}
        {statusMessage.text && (
          <div className={`toast-notification ${statusMessage.type}`}>
            <span className="toast-icon">
              {statusMessage.type === 'success' ? '✓' : statusMessage.type === 'error' ? '✕' : 'ℹ'}
            </span>
            <span>{statusMessage.text}</span>
          </div>
        )}

        {/* Legend */}
        <div className="legend-strip">
          <div className="legend-item"><span className="seat-sample available"></span> Available</div>
          <div className="legend-item"><span className="seat-sample locked"></span> Held / Locked</div>
          <div className="legend-item"><span className="seat-sample booked"></span> Booked (Sold)</div>
          <div className="legend-item"><span className="seat-sample selected"></span> Your Selection</div>
        </div>

        {/* Stadium Stage */}
        <div className="stadium-stage">
          <div className="stage-arch">
            <span className="stage-text">★ LIVE STAGE / PERFORMANCE PLATFORM ★</span>
          </div>
          <div className="stage-glow"></div>
        </div>

        {/* Stadium Seating Layout */}
        <div className="stadium-layout">
          {['A', 'B', 'C', 'D'].map(row => {
            const sortedSeats = getSortedRowSeats(row);
            const isVip = row === 'A' || row === 'B';
            const leftWing = sortedSeats.slice(0, 5);
            const rightWing = sortedSeats.slice(5, 10);

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
                  <span className="price-tag">₹{isVip ? '5,000' : '2,500'}</span>
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
              <h4>Seat {selectedSeat.seatNumber} Reserved</h4>
              <p>Category: {selectedSeat.seatNumber.startsWith('A') || selectedSeat.seatNumber.startsWith('B') ? 'VIP Platinum' : 'Standard'} • Total: <strong>₹{selectedSeat.price}</strong></p>
            </div>

            <div className="drawer-actions">
              <button className="btn-release" onClick={handleReleaseSeat}>Release Seat</button>
              <button className="btn-pay" onClick={handleConfirmBooking}>Confirm &amp; Pay ₹{selectedSeat.price}</button>
            </div>
          </div>
        )}

        {/* Confirmed Ticket Modal */}
        {bookedTicket && (
          <div className="modal-backdrop">
            <div className="ticket-modal">
              <div className="modal-header">
                <h3>PASS ISSUED</h3>
                <button className="close-btn" onClick={() => setBookedTicket(null)}>✕</button>
              </div>
              <div className="ticket-body">
                <div className="ticket-event">{event?.name || 'Coldplay Tour'}</div>
                <div className="ticket-grid">
                  <div>
                    <span className="t-lbl">SEAT NUMBER</span>
                    <span className="t-val accent">{bookedTicket.seatNumber}</span>
                  </div>
                  <div>
                    <span className="t-lbl">VENUE</span>
                    <span className="t-val">{event?.venue}</span>
                  </div>
                  <div>
                    <span className="t-lbl">STATUS</span>
                    <span className="t-val success">CONFIRMED (PAID)</span>
                  </div>
                  <div>
                    <span className="t-lbl">PRICE</span>
                    <span className="t-val">₹{bookedTicket.price}</span>
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

      </div>
    </div>
  );

  function renderSeat(seat) {
    const isSelected = selectedSeat?.id === seat.id;
    let seatClasses = 'stadium-seat';

    if (seat.status === 'AVAILABLE') seatClasses += ' seat-available';
    else if (seat.status === 'LOCKED') seatClasses += ' seat-locked';
    else if (seat.status === 'BOOKED') seatClasses += ' seat-booked';

    if (isSelected) seatClasses += ' seat-selected';

    return (
      <button
        key={seat.id}
        className={seatClasses}
        onClick={() => handleSeatClick(seat)}
        title={`${seat.seatNumber} • ₹${seat.price} (${seat.status})`}
      >
        <span className="seat-cushion"></span>
        <span className="seat-label">{seat.seatNumber}</span>
      </button>
    );
  }
}

export default App;
