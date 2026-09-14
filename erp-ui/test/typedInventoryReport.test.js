const test=require('node:test'),assert=require('node:assert/strict'),fs=require('fs'),path=require('path'),vm=require('vm'),babel=require('@babel/core'),Vue=require('vue'),compiler=require('vue-template-compiler');
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return{promise,resolve,reject}},tick=async()=>{await new Promise(r=>setImmediate(r));await Vue.nextTick()};
function load(relative,extras={},propsData){
 const file=path.resolve(__dirname,'../src',relative),parts=compiler.parseComponent(fs.readFileSync(file,'utf8'));assert.deepEqual(compiler.compile(parts.template.content).errors,[]);
 const env={dept:'20'},store=Vue.observable({getters:{id:'7',token:'a'}}),module={exports:{}};
 vm.runInNewContext(babel.transformSync(parts.script.content,{filename:file,babelrc:false,configFile:false,plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code,{module,exports:module.exports,console,Promise,window:{addEventListener(){},removeEventListener(){}},require(id){
  if(id in extras)return extras[id];if(id==='@/utils/uiOperationScope')return require('../src/utils/uiOperationScope');
  if(id==='@/utils/shopContext')return{getSelectedDeptId:()=>env.dept,getSelectedDeptContext:()=>({isWarehouse:true}),getSelectedDeptName:()=> 'Warehouse'};return {};
 }});
 const c=new Vue({...module.exports.default,created:undefined,propsData,beforeCreate(){this.$store=store;this.addDateRange=q=>q}});return{c,env,store};
}
function selector(){const calls=[];const h=load('views/inventory/components/ReportItemSelect.vue',{'@/api/inventory/report':{listReportItemOptions:q=>{const d=deferred();calls.push({q,...d});return d.promise}}},{itemType:'',value:undefined});return{...h,calls};}
const item=(type,id='1')=>({itemType:type,itemId:id,itemCode:type+'-'+id,itemName:type+' item'});
test('report material picker uses typed identities for equal IDs and only emits loaded authorized choices',async()=>{
 const h=selector(),emitted=[];h.c.$on('change',value=>emitted.push(value));const p=h.c.search('');h.calls[0].resolve({data:[item('oe'),item('gift'),item('product')]});await p;
 assert.equal(h.c.options.length,3);assert.deepEqual(h.c.options.map(x=>h.c.key(x)),['oe:1','gift:1','product:1']);h.c.select('oe:1');assert.equal(emitted[0].itemType,'oe');h.c.select('oe:999');assert.equal(emitted.length,1);
});
test('changing report material type discards obsolete options; failed query has explicit retry',async()=>{
 const h=selector(),old=h.c.search('old');h.c.$props.itemType='gift';await tick();const fresh=h.c.search('new');h.calls[0].resolve({data:[item('oe')]});await old;assert.equal(h.c.options.length,0);assert.equal(h.c.loading,true);
 h.calls[1].reject(Error('offline'));await fresh;assert.ok(h.c.error);assert.equal(h.c.loaded,false);const retry=h.c.search(h.c.keyword);h.calls[2].resolve({data:[item('gift'),item('oe')]});await retry;assert.equal(h.c.options.length,1);assert.equal(h.c.options[0].itemType,'gift');
});
test('a valid selection survives its corresponding type update while context switch removes it',async()=>{
 const h=selector();h.c.$props.value='oe:1';h.c.$props.itemType='oe';await tick();assert.equal(h.calls.length,1);h.calls[0].resolve({data:[item('oe')]});await tick();assert.equal(h.c.value,'oe:1');
 const emitted=[];h.c.$on('input',v=>emitted.push(v));const old=h.c.search('');h.env.dept='30';h.c.reset();h.env.dept='20';h.c.reset();h.calls.at(-1).resolve({data:[item('oe')]});await old;assert.equal(h.c.options.length,0);assert.equal(emitted.at(-1),undefined);
});
test('warning and summary query share exact material pair and unknown threshold is never shown as zero',()=>{
 const h=load('views/inventory/report/index.vue');h.c.handleItemChange(item('gift','9007199254740993'));let q=h.c.buildQuery(true);assert.equal(q.itemType,'gift');assert.equal(q.itemId,'9007199254740993');assert.equal(q.productId,undefined);
 const summary=h.c.buildQuery(false);assert.equal(summary.itemType,q.itemType);assert.equal(summary.itemId,q.itemId);assert.equal(summary.pageNum,undefined);
 assert.equal(h.c.stockStatusLabel({itemType:'gift',safetyStockMin:null,availableQuantity:0}),'阈值未配置');h.c.handleItemTypeChange();assert.equal(h.c.buildQuery(false).itemId,undefined);
});
