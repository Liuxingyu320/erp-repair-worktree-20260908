import axios from 'axios'
import { Loading, Message } from '@/plugins/element-services'
import { getToken } from '@/utils/auth'
import { buildSessionAuthHeaders, shouldUseSessionCredentials } from '@/utils/sessionMode'
import errorCode from '@/utils/errorCode'
import { blobValidate } from "@/utils/common"

const baseURL = process.env.VUE_APP_BASE_API
let downloadLoadingInstance

export default {
  zip(url, name) {
    var url = baseURL + url
    downloadLoadingInstance = Loading.service({ text: "正在下载数据，请稍候", spinner: "el-icon-loading", background: "rgba(0, 0, 0, 0.7)", })
    axios({
      method: 'get',
      url: url,
      responseType: 'blob',
      headers: buildSessionAuthHeaders(getToken()),
      withCredentials: shouldUseSessionCredentials()
    }).then(async (res) => {
      const isBlob = blobValidate(res.data)
      if (isBlob) {
        const blob = new Blob([res.data], { type: 'application/zip' })
        await this.saveAs(blob, name)
      } else {
        this.printErrMsg(res.data)
      }
      downloadLoadingInstance.close()
    }).catch((r) => {
      console.error(r)
      Message.error('下载文件出现错误，请联系管理员！')
      downloadLoadingInstance.close()
    })
  },
  saveAs(text, name, opts) {
    return import(
      /* webpackChunkName: "chunk-download" */
      'file-saver'
    ).then(module => module.saveAs(text, name, opts))
  },
  async printErrMsg(data) {
    const resText = await data.text()
    const rspObj = JSON.parse(resText)
    const errMsg = errorCode[rspObj.code] || rspObj.msg || errorCode['default']
    Message.error(errMsg)
  }
}
