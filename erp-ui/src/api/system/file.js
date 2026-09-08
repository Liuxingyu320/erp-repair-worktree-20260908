import request from '@/utils/request'

export function deleteFile(fileUrl) {
  return request({
    url: '/file/delete',
    method: 'delete',
    params: { fileUrl }
  })
}
