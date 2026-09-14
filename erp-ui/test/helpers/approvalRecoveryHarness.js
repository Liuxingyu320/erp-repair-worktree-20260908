const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),babel=require('@babel/core')
module.exports=function approvalRecoveryHarness(api,env={dept:'10'}) {
  const file=path.resolve(__dirname,'../../src/mixins/approvalCommandRecovery.js')
  const code=babel.transformSync(fs.readFileSync(file,'utf8'),{babelrc:false,configFile:false,plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code
  const module={exports:{}}
  vm.runInNewContext(code,{module,exports:module.exports,console,Promise,window:{addEventListener(){},removeEventListener(){}},require:id=>{
    if(id.startsWith('@/api/'))return api
    if(id==='@/utils/shopContext')return {getSelectedDeptId:()=>env.dept}
    if(id==='@/utils/uiOperationScope')return require('../../src/utils/uiOperationScope')
    if(id==='@/utils/approvalCommandRecovery')return require('../../src/utils/approvalCommandRecovery')
    throw Error(id)
  }})
  return module.exports
}
