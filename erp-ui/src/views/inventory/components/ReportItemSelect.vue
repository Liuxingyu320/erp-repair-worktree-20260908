<template>
  <div class="report-item-select">
    <el-select :value="value" filterable remote clearable :remote-method="search" :loading="loading"
      placeholder="搜索物料名称/编码" style="width:240px" @change="select" @visible-change="visible">
      <el-option v-for="item in options" :key="key(item)" :value="key(item)" :label="label(item)" :disabled="loading || !!error" />
    </el-select>
    <span v-if="error" role="alert">候选加载失败 <el-button type="text" :disabled="loading" @click="search(keyword)">重试</el-button></span>
  </div>
</template>
<script>
import { listReportItemOptions } from '@/api/inventory/report'
import { getSelectedDeptId } from '@/utils/shopContext'
const { createUiOperationScope } = require('@/utils/uiOperationScope')
export default {
  name:'ReportItemSelect', props:{value:String,itemType:String},
  data(){return{options:[],keyword:'',loading:false,error:'',loaded:false}},
  watch:{itemType(){this.reset(!!this.value && this.value.startsWith((this.itemType||'')+':'))},'$store.getters.id'(){this.reset()},'$store.getters.token'(){this.reset()}},
  created(){this._deptChanged=()=>this.reset();window.addEventListener('erp:dept-changed',this._deptChanged)},
  beforeDestroy(){window.removeEventListener('erp:dept-changed',this._deptChanged);this.scope().deactivate()},
  methods:{
    scope(){if(!this._scope)this._scope=createUiOperationScope(()=>({actor:String(this.$store.getters.id||''),session:String(this.$store.getters.token||''),dept:String(getSelectedDeptId()||''),type:this.itemType||'',keyword:this.keyword}));return this._scope},
    reset(preserveSelection=false){this.scope().invalidate();this.options=[];this.loaded=false;this.loading=false;this.error='';this.keyword='';if(!preserveSelection){this.$emit('input',undefined);this.$emit('change',null)}else this.search('')},
    key(item){return item.itemType+':'+String(item.itemId)},
    label(item){return({product:'商品',oe:'OE',gift:'礼盒'}[item.itemType]||'物料')+' · '+(item.itemCode||'')+' '+(item.itemName||'')},
    visible(open){if(open&&!this.loaded)this.search(this.keyword)},
    async search(keyword=''){
      this.keyword=String(keyword||'').trim();const scope=this.scope(),operation=scope.begin('options');this.loading=true;this.error='';
      try{
        const response=await listReportItemOptions({keyword:this.keyword,itemType:this.itemType||undefined,limit:20});if(!scope.isCurrent(operation))return;
        if(!response||!Array.isArray(response.data))throw Error('物料响应未确认');
        const keys=new Set();this.options=response.data.filter(item=>{
          if(!item||!['product','oe','gift'].includes(item.itemType)||!/^\d+$/.test(String(item.itemId||''))||(this.itemType&&this.itemType!==item.itemType))return false;
          const key=this.key(item);if(keys.has(key))return false;keys.add(key);return true;
        });this.loaded=true;
      }catch(error){if(scope.isCurrent(operation)){this.error='候选加载失败';this.options=[];this.loaded=false}}
      finally{if(scope.isCurrent(operation))this.loading=false}
    },
    select(value){if(!value){this.$emit('input',undefined);this.$emit('change',null);return}if(this.loading||this.error)return;const selected=this.options.find(item=>this.key(item)===value);if(!selected)return;this.$emit('input',value);this.$emit('change',selected)}
  }
}
</script>
<style scoped>.report-item-select{display:inline-flex;flex-direction:column}.report-item-select>span{color:#c45656;font-size:12px}</style>
