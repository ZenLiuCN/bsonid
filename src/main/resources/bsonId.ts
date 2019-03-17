namespace Bson {
    const BsonIdRegx = /^[0-9a-fA-F]{24}$/
    const BsonShortIdRegx = /^[0-9a-zA-Z\-\_]{16}$/
    const code = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_'
    const compress = function (hexBsonId: string) {
        if (hexBsonId.toLowerCase().match(BsonIdRegx) == null) {
            throw Error(`Invalid BsonId ${hexBsonId},should be 24 char for HEX`)
        }
        let res = ""
        const ar = hexBsonId.split("").map(c => char2int(c))
        for (let i = 0; i < ar.length; i += 3) {
            const pre = ar[i]
            const mid = ar[i + 1]
            const end = ar[i + 2]
            res += int2char((pre << 2) + (mid >> 2))
            res += int2char(((mid & 3 )<< 4) + end)
        }
        return res
    }
    const decompress = function (hexShortId: string) {
        if (hexShortId.match(BsonShortIdRegx) == null) {
            throw Error(`Invalid BsonShortId ${hexShortId},should be 16 char `)
        }
        let res = ""
        const ar = hexShortId.split("").map(c => char2int(c))
        for (let i = 0; i < ar.length; i += 2) {
            const pre = ar[i]
            const end = ar[i + 1]
            res += int2char(pre >> 2)
            res += int2char(((pre & 3 )<< 2 )+ (end >> 4))
            res += int2char(end & 15)
        }
        return res
    }
    const int2char = function (i: number) {
        if (i < 0 || i > 63) {
            throw Error(`invalid integer ${i} for char`)
        }
        return code[i]
    }
    const char2int = function (i: string) {
        if (i.length != 1) {
            throw Error(`invalid char ${i} for integer`)
        }
        const idx = code.indexOf(i)
        if (idx < 0) {
            throw Error(`invalid char code ${i} for integer`)
        }
        return idx
    }
    const hex = (length: number, n: number) => {
        const c = n.toString(16);
        return (c.length === length) ? c : "00000000".substring(c.length, length) + c;
    }
    class Cacher {
        _inc: number = -1
        _machineIdentifier: number = -1
        _processIdentifier: number = -1
        private _loaded: boolean = false
        private _useLocal: boolean = false
        private _useSession: boolean = false
        private _useCookie: boolean = false

        load() {
            this._loaded = false
            this.fromLocalStore()
            if (!this._loaded) {
                this.fromSessionStore()
            } else {
                this.saveSessionStore()
                return this
            }
            if (!this._loaded) {
                this.fromCookie()
            } else {
                this.saveCookie()
                return this
            }
            if (!this._loaded) {
                this._machineIdentifier = Math.floor(Math.random() * (16777216))
                this._processIdentifier = Math.floor(Math.random() * (65536))
                this._inc = 0
            }
            this._loaded = true
            return this

        }


        save() {
            if (this._useLocal) {
                this.saveLocalStore()
            } else if (this._useSession) {
                this.saveSessionStore()
            } else if (this._useCookie) {
                this.saveCookie()
            } else {
                console.error("could not save ")
            }
        }

        private saveSessionStore() {
            sessionStorage.setItem("bson", JSON.stringify(
                {
                    _inc: this._inc,
                    _machineIdentifier: this._machineIdentifier,
                    _processIdentifier: this._processIdentifier,
                }
            ))
        }

        private saveLocalStore() {
            localStorage.setItem("bson", JSON.stringify(
                {
                    _inc: this._inc,
                    _machineIdentifier: this._machineIdentifier,
                    _processIdentifier: this._processIdentifier,
                }
            ))
        }

        private saveCookie() {
            if (document.hasOwnProperty("cookie")) {
                const tab = document.cookie.split(";").map(e => e.split("="))
                const bson = tab.filter(e => e[0] == "bson")
                if (bson.length == 1) {
                    bson[0][1] = JSON.stringify({
                        _inc: this._inc,
                        _machineIdentifier: this._machineIdentifier,
                        _processIdentifier: this._processIdentifier,
                    })
                } else {
                    tab.push(["bson", JSON.stringify(
                        {
                            _inc: this._inc,
                            _machineIdentifier: this._machineIdentifier,
                            _processIdentifier: this._processIdentifier,
                        }
                    )])
                }
                document.cookie = tab.map(e => e.join("=")).join(";")

            }
        }

        private fromSessionStore() {
            if (sessionStorage) {
                this._useSession = true
            }
            let cache = sessionStorage.getItem("bson")
            if (cache == null) {
                this.genarate()
                this.save()
                this._loaded = true
                return
            }
            const load = JSON.parse(cache)
            this._machineIdentifier = load._machineIdentifier
            this._processIdentifier = load._processIdentifier
            this._inc = load._inc
            this._loaded = true
        }

        private fromLocalStore() {
            if (localStorage) {
                this._useLocal = true
            }
            let cache = localStorage.getItem("bson")
            if (cache == null) {
                this.genarate()
                this.save()
                this._loaded = true
                return
            }
            const load = JSON.parse(cache)
            this._machineIdentifier = load._machineIdentifier
            this._processIdentifier = load._processIdentifier
            this._inc = load._inc
            this._loaded = true
        }
        private genarate(){
            this._machineIdentifier = Math.floor(Math.random() * (16777216))
            this._processIdentifier = Math.floor(Math.random() * (65536))
            this._inc = 0
        }
        private fromCookie() {
            if (document.hasOwnProperty("cookie")) {
                this._useCookie = true
                const tab = document.cookie.split(";").map(e => e.split("="))
                const bson = tab.filter(e => e[0] == "bson")
                if (bson.length == 1) {
                    const load = JSON.parse(bson[0][1])
                    this._machineIdentifier = load._machineIdentifier
                    this._processIdentifier = load._processIdentifier
                    this._inc = load._inc
                    this._loaded = true
                    return
                }else{
                    this.genarate()
                    this.save()
                    this._loaded = true
                    return
                }
            }
        }

        private get inc() {
            this._inc = (this._inc + 1) % 0xffffff
            this.save()
            return this._inc
        }

        private get timestamp() {
            return Math.round(new Date().getTime() / 1000)
        }

        new() {
            this.load()
            return hex(8, this.timestamp)
                + hex(6, this._machineIdentifier)
                + hex(4, this._processIdentifier)
                + hex(6, this.inc)
        }

    }
    export const validateLong = (hex: string|null) => hex?BsonIdRegx.test(hex):false
    export const validateShort = (hex: string|null) => hex?BsonShortIdRegx.test(hex):false
    const state = new Cacher().load()
    export class BsonId {
        get timestamp(): number {
            return parseInt(this.buf.substr(0, 8), 16)
        }

        get machineIdentifier(): number {
            return parseInt(this.buf.substr(9, 6), 16)
        }

        get processIdentifier(): number {
            return parseInt(this.buf.substr(15, 4), 16)
        }

        get counter(): number {
            return parseInt(this.buf.substr(19, 6), 16)
        }

        get date() {
            return new Date(this.timestamp * 1000)
        }

        private buf: string = ""

        constructor(hex: string | null = null) {
            if (validateLong(hex)) {
                this.buf = hex!!
            } else {
                this.buf = state.new()
            }
        }

        toString() {
            return this.buf
        }

        toBsonShortId() {
            return new BsonShortId(this.toBsonShortString())
        }

        toBsonShortString() {
            return compress(this.buf)
        }
    }
    export class BsonShortId {
        get timestamp(): number {
            return this.id.timestamp
        }

        get machineIdentifier(): number {
            return this.id.machineIdentifier
        }

        get processIdentifier(): number {
            return this.id.processIdentifier
        }

        get counter(): number {
            return this.id.counter
        }

        get date() {
            return new Date(this.timestamp * 1000)
        }

        private id: BsonId
        private buf: string

        constructor(hex: string | null = null) {
            if (validateShort(hex)) {
                this.buf = hex!!
                this.id = new BsonId(decompress(hex!!))
            } else {
                this.id = new BsonId()
                this.buf = this.id.toBsonShortString()
            }

        }

        toString() {
            return this.id.toString()
        }

        toBsonId() {
            return this.id
        }

        toBsonString() {
            return this.id.toString()
        }
    }
    export function fromHex(hex: string | null) {
        switch (true) {
            case hex===null: return new BsonId().toBsonShortId()
            case validateLong(hex): return new BsonId(hex)
            case validateShort(hex): return new BsonId(hex)
            default: throw Error(`Invalid BsonId ${hex} `)
        }
    }
}
const BsonHelper = {
    from(hex: string | null) {
        return Bson.fromHex(hex)
    },
    get() {
        return Bson.fromHex(null)
    },
    getFull(){
        return Bson.fromHex(null)['toBsonId']()
    },
    validateShort:Bson.validateShort,
    validate:Bson.validateLong
}
export default BsonHelper

