const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const assert = require('node:assert/strict')
const Vue = require('vue')
const babel = require('@babel/core')
const compiler = require('vue-template-compiler')
const root = path.resolve(__dirname, '../..')
const flush = async () => { for (let i = 0; i < 10; i++) { await Promise.resolve(); await Vue.nextTick() } }
const deferred = () => { let resolve, reject; const promise = new Promise((yes,no) => { resolve=yes;reject=no }); return { promise,resolve,reject } }
function harness(file, props = {}, allowed = ['self','read','adjust','rule','convert'], options = {}) {
  const requests=[], confirms=[], prompts=[], storage=new Map(), events=new Map(), warnings=[]
  const state={dept:'100',storageFails:false}, route=Vue.observable({fullPath:'/oa/attendance',query:{}}), store=Vue.observable({getters:{id:'7'},state:{user:{id:'7'}}})
  let nonce=0
  const request=options=>new Promise((resolve,reject)=>requests.push({options,resolve,reject}))
  const sessionStorage={getItem:key=>storage.get(key)||null,setItem:(key,value)=>{if(state.storageFails)throw Error('storage disabled');storage.set(key,value)},removeItem:key=>storage.delete(key)}
  const window={crypto:{randomUUID:()=>`00000000-0000-4000-a000-${String(++nonce).padStart(12,'0')}`},addEventListener:(name,fn)=>events.set(name,fn),removeEventListener:(name,fn)=>{if(events.get(name)===fn)events.delete(name)}}
  const cache={}
  function load(relative) {
    if(cache[relative])return cache[relative]
    const filename=path.join(root,relative),source=fs.readFileSync(filename,'utf8'),script=relative.endsWith('.vue')?compiler.parseComponent(source).script.content:source
    const code=babel.transformSync(script,{filename,babelrc:false,configFile:false,plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code
    const module={exports:{}}
    const dependency=id=>{
      if(options.shallow && id.endsWith('.vue'))return {}
      if(id==='@/utils/request')return request
      if(id==='@/utils/shopContext')return {getSelectedDeptId:()=>state.dept,getSelectedDeptContext:()=>({deptId:state.dept,isStore:true})}
      if(id==='@/utils/permission')return {checkPermi:permissions=>permissions.some(p=>allowed.includes(p.split(':').pop()))}
      if(id.startsWith('@/'))return load('src/'+id.slice(2)+(path.extname(id)?'':'.js'))
      if(id.startsWith('.'))return load(path.posix.normalize(path.posix.dirname(relative)+'/'+id)+(path.extname(id)?'':'.js'))
      throw Error('unmapped '+id)
    }
    vm.runInNewContext(code,{module,exports:module.exports,require:dependency,window,sessionStorage,console,Date,Math,JSON,Promise,Set,Map,BigInt,Number,Object,String,Array,Error,URL,Blob,setTimeout,clearTimeout},{filename})
    return cache[relative]=module.exports.default||module.exports
  }
  const definition=load(file)
  const c=new Vue({...definition,propsData:props,beforeCreate(){this.$store=store;this.$route=route;this.$auth={hasPermi:p=>allowed.includes(p)||allowed.includes(p.split(':').pop())};this.$confirm=(...args)=>{const d=deferred();confirms.push({...d,args});return d.promise};this.$prompt=(...args)=>{const d=deferred();prompts.push({...d,args});return d.promise}}})
  function take(suffix, method) { const index=requests.findIndex(r=>r.options.url.endsWith(suffix)&&(!method||r.options.method===method));assert.ok(index>=0,'missing '+suffix+' among '+requests.map(r=>r.options.url));return requests.splice(index,1)[0] }
  function resolve(suffix,data,method) { take(suffix,method).resolve({code:200,data}) }
  function render() { const source=fs.readFileSync(path.join(root,file),'utf8'),parsed=compiler.parseComponent(source),code=compiler.compileToFunctions(parsed.template.content);assert.deepEqual(compiler.compile(parsed.template.content).errors,[]);c.$options.render=code.render;c.$options.staticRenderFns=code.staticRenderFns;return c._render() }
  return {c,state,window,route,store,requests,confirms,prompts,storage,events,warnings,load,take,resolve,render,flush}
}
const type=(id='1',overrides={})=>({leaveTypeId:id,typeCode:'ANNUAL',typeName:'年假',unitMode:'DAY',balanceRequired:true,...overrides})
const bucket=(overrides={})=>({bucketId:'91',rowVersion:'3',ruleId:'41',ruleVersion:2,displayUnit:'DAYS',minutesPerDay:'480',grantedUnits:'4800000000',reservedUnits:'0',consumedUnits:'0',expiredUnits:'0',carriedUnits:'0',expiryState:'OPEN',expiresOn:'2026-12-31',sourceType:'ANNUAL',...overrides})
const balance=(userId='7',overrides={})=>({userId,leaveTypeId:'1',status:'READY',ruleName:'测试配置',ruleVersion:2,availableUnits:'4800000000',reservedUnits:'0',consumedUnits:'0',displayUnit:'DAYS',minutesPerDay:'480',buckets:[bucket()],...overrides})
const source=(overrides={})=>({dayResultId:'9007199254741001',rowVersion:'8',userId:'17',userName:'甲',shopId:'100',businessDate:'2026-09-12',workedMinutes:600,scheduledMinutes:480,settledAt:'2026-09-12 20:00:00',...overrides})
const context=(overrides={})=>({source:source(),status:'READY',rawOvertimeMinutes:120,offsetMinutes:20,transferredMinutes:10,availableMinutes:90,history:[],...overrides})
module.exports={harness,flush,deferred,type,bucket,balance,source,context}
