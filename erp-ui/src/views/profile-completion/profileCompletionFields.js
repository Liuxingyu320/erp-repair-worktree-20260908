const PROFILE_COMPLETION_FIELDS = [
  {
    key: "nickName",
    label: "姓名",
    type: "text",
    placeholder: "请输入姓名",
    maxlength: 30,
    autocomplete: "name"
  },
  {
    key: "phonenumber",
    label: "手机号",
    type: "tel",
    placeholder: "请输入本人手机号",
    maxlength: 11,
    autocomplete: "tel",
    inputmode: "numeric"
  },
  {
    key: "sex",
    label: "性别",
    type: "select",
    placeholder: "请选择性别",
    options: [
      { label: "男", value: "0" },
      { label: "女", value: "1" }
    ]
  },
  {
    key: "idType",
    label: "证件类型",
    type: "select",
    placeholder: "请选择证件类型",
    allowCreate: true,
    options: [
      { label: "居民身份证", value: "居民身份证" },
      { label: "护照", value: "护照" },
      { label: "港澳居民来往内地通行证", value: "港澳居民来往内地通行证" },
      { label: "台湾居民来往大陆通行证", value: "台湾居民来往大陆通行证" },
      { label: "其他", value: "其他" }
    ]
  },
  {
    key: "idNumber",
    label: "证件号码",
    type: "text",
    placeholder: "请输入证件号码",
    maxlength: 64,
    autocomplete: "off"
  },
  {
    key: "registeredResidence",
    label: "户籍地址",
    type: "text",
    placeholder: "请输入户籍地址",
    maxlength: 255,
    autocomplete: "address-level1"
  },
  {
    key: "currentAddress",
    label: "现居住地址",
    type: "textarea",
    placeholder: "请输入现居住地址",
    maxlength: 255,
    autocomplete: "street-address"
  }
]

const PROFILE_COMPLETION_FIELD_KEYS = PROFILE_COMPLETION_FIELDS.map(field => field.key)

module.exports = {
  PROFILE_COMPLETION_FIELDS,
  PROFILE_COMPLETION_FIELD_KEYS
}
