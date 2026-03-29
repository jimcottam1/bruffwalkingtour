/**
 * TourController manages all tour state.
 * Reads and writes to sessionStorage so state survives page navigation.
 * No DOM, no map, no geolocation — only operates on data.
 */

import { haversineDistance } from './utils.js';

const STATE_KEY = 'bruff_tour_state';

export class TourController {
  /**
   * @param {Array} waypoints - WAYPOINTS array from data.js
   * @param {Storage} storage - injectable for testing; defaults to sessionStorage
   */
  constructor(waypoints, storage = null) {
    this._waypoints = waypoints;
    this._storage =
      storage ??
      (typeof sessionStorage !== 'undefined' ? sessionStorage : new MemoryStorage());
    this._state = this._loadState();
  }

  _loadState() {
    try {
      const raw = this._storage.getItem(STATE_KEY);
      if (raw) return JSON.parse(raw);
    } catch {
      // Corrupt storage — start fresh
    }
    return this._defaultState();
  }

  _defaultState() {
    return { currentIndex: 0, visitedIds: [], lastNotifiedId: null };
  }

  _saveState() {
    this._storage.setItem(STATE_KEY, JSON.stringify(this._state));
  }

  /** Reset tour to the beginning and clear storage. */
  reset() {
    this._state = this._defaultState();
    this._saveState();
  }

  getCurrentWaypoint() {
    return this._waypoints[this._state.currentIndex] ?? null;
  }

  getCurrentIndex() {
    return this._state.currentIndex;
  }

  getWaypointCount() {
    return this._waypoints.length;
  }

  /** Returns true only when ALL waypoints have been visited (index is past the end). */
  isComplete() {
    return this._state.currentIndex >= this._waypoints.length;
  }

  isVisited(waypointId) {
    return this._state.visitedIds.includes(waypointId);
  }

  getVisitedIds() {
    return [...this._state.visitedIds];
  }

  /**
   * Mark the current waypoint as visited and advance to the next.
   * Idempotent — safe to call twice without skipping a waypoint.
   * @returns {boolean} true if the tour is now complete
   */
  markCurrentVisited() {
    if (this.isComplete()) return true;
    const current = this.getCurrentWaypoint();
    if (current && !this.isVisited(current.id)) {
      this._state.visitedIds.push(current.id);
    }
    this._state.currentIndex++;
    this._state.lastNotifiedId = null;
    this._saveState();
    return this.isComplete();
  }

  /**
   * Check whether the user has arrived at the current waypoint.
   * Mirrors Android's one-notification-per-waypoint pattern:
   * only fires once until the tour advances or resets.
   * @param {number} lat
   * @param {number} lon
   * @returns {object|null} waypoint object if newly arrived, null otherwise
   */
  checkProximity(lat, lon) {
    const wp = this.getCurrentWaypoint();
    if (!wp) return null;
    const distance = haversineDistance(lat, lon, wp.latitude, wp.longitude);
    if (distance <= wp.proximityRadius && this._state.lastNotifiedId !== wp.id) {
      this._state.lastNotifiedId = wp.id;
      this._saveState();
      return wp;
    }
    return null;
  }

  /**
   * Distance from a coordinate to the current target waypoint.
   * Returns null when the tour is complete.
   */
  getDistanceToCurrent(lat, lon) {
    const wp = this.getCurrentWaypoint();
    if (!wp) return null;
    return haversineDistance(lat, lon, wp.latitude, wp.longitude);
  }
}

/** In-memory Storage implementation for environments without sessionStorage (e.g. Node tests). */
class MemoryStorage {
  constructor() {
    this._d = {};
  }
  getItem(k) {
    return this._d[k] ?? null;
  }
  setItem(k, v) {
    this._d[k] = String(v);
  }
  removeItem(k) {
    delete this._d[k];
  }
  clear() {
    this._d = {};
  }
}
