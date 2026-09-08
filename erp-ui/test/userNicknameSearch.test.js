const assert = require('assert')
const fs = require('fs')
const path = require('path')

const userViewPath = path.resolve(__dirname, '../src/views/system/user/index.vue')
const mapperPath = path.resolve(__dirname, '../../erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml')

const userView = fs.readFileSync(userViewPath, 'utf8')
const mapper = fs.readFileSync(mapperPath, 'utf8')

assert.ok(
  userView.includes('label="姓名"') &&
    userView.includes('prop="nickName"') &&
    userView.includes('v-model="queryParams.nickName"') &&
    userView.includes('placeholder="请输入姓名"'),
  'user search form should provide a name input bound to queryParams.nickName'
)

assert.ok(
  userView.includes('nickName: undefined'),
  'user query params should initialize nickName so reset and requests keep the nickname filter predictable'
)

assert.ok(
  mapper.includes('nickName != null and nickName !=') &&
    mapper.includes('u.nick_name like concat') &&
    mapper.includes('#{nickName}'),
  'user list mapper should fuzzy filter by u.nick_name when nickName is provided'
)

console.log('userNicknameSearch tests passed')
