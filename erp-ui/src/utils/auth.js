import Cookies from 'js-cookie'
const { authCookieOptions } = require("./authCookiePolicy")

const TokenKey = 'Admin-Token'

const ExpiresInKey = 'Admin-Expires-In'

export function getToken() {
  return Cookies.get(TokenKey)
}

export function setToken(token) {
  return Cookies.set(TokenKey, token, authCookieOptions())
}

export function removeToken() {
  return Cookies.remove(TokenKey, authCookieOptions())
}

export function getExpiresIn() {
  return Cookies.get(ExpiresInKey) || -1
}

export function setExpiresIn(time) {
  return Cookies.set(ExpiresInKey, time, authCookieOptions())
}

export function removeExpiresIn() {
  return Cookies.remove(ExpiresInKey, authCookieOptions())
}
