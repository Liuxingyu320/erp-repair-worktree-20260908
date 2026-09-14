import Vue from 'vue'
import store from '@/store'
import DataDict from '@/utils/dict'
import { getDicts as getDicts } from '@/api/system/dict/data'

function searchDictByKey(dict, key) {
  if (key == null && key == "") {
    return null
  }
  try {
    for (let i = 0; i < dict.length; i++) {
      if (dict[i].key == key) {
        return dict[i].value
      }
    }
  } catch (e) {
    return null
  }
}

function install() {
  Vue.use(DataDict, {
    metas: {
      '*': {
        labelField: 'dictLabel',
        valueField: 'dictValue',
        request(dictMeta) {
          const read = remaining => {
            const cached = searchDictByKey(store.getters.dict, dictMeta.type)
            if (cached) return Promise.resolve(cached)
            const revision = store.state.dict.revisions[dictMeta.type] || 0
            const epoch = store.state.dict.epoch
            return getDicts(dictMeta.type).then(res => {
              if (revision !== (store.state.dict.revisions[dictMeta.type] || 0) || epoch !== store.state.dict.epoch) {
                if (remaining) return read(remaining - 1)
                throw Error('字典已变化，请重新读取')
              }
              store.dispatch('dict/setDict', { key: dictMeta.type, value: res.data, revision, epoch })
              return res.data
            })
          }
          return read(1)
        },
      },
    },
  })
}

export default {
  install,
}