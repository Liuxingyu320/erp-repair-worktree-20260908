import Vue from 'vue'
const state = { dict: [], revisions: {}, epoch: 0 }
const mutations = {
  SET_DICT(state, { key, value, revision, epoch }) {
    if (key == null || key === '') return
    if (revision !== undefined && (revision !== (state.revisions[key] || 0) || epoch !== state.epoch)) return
    state.dict = state.dict.filter(item => item.key !== key)
    state.dict.push({ key, value })
  },
  REMOVE_DICT(state, key) {
    state.dict = state.dict.filter(item => item.key !== key)
    Vue.set(state.revisions, key, (state.revisions[key] || 0) + 1)
  },
  CLEAN_DICT(state) { state.dict = []; state.epoch += 1; state.revisions = {} }
}
const actions = {
  setDict({ commit }, data) { commit('SET_DICT', data) },
  removeDict({ commit }, key) { commit('REMOVE_DICT', key) },
  cleanDict({ commit }) { commit('CLEAN_DICT') }
}
export default { namespaced: true, state, mutations, actions }
